/*
 * Copyright 2010-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mybatis.spring.batch;

import static org.springframework.util.Assert.notNull;
import static org.springframework.util.ClassUtils.getShortName;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.batch.item.database.AbstractPagingItemReader;

/**
 * 基于分页的 MyBatis 的读取器（继承AbstractPagingItemReader）
 * {@code org.springframework.batch.item.ItemReader} for reading database records using MyBatis in a paging fashion.
 * <p>
 * Provided to facilitate the migration from Spring-Batch iBATIS 2 page item readers to MyBatis 3.
 *
 * @author Eduardo Macarron
 *
 * @since 1.1.0
 */
public class MyBatisPagingItemReader<T> extends AbstractPagingItemReader<T> {

  // 查询编号
  private String queryId;

  // SqlSessionFactory 对象
  private SqlSessionFactory sqlSessionFactory;

  // SqlSessionTemplate 对象
  private SqlSessionTemplate sqlSessionTemplate;

  // 参数值的映射
  private Map<String, Object> parameterValues;

  private Supplier<Map<String, Object>> parameterValuesSupplier;

  public MyBatisPagingItemReader() {
    setName(getShortName(MyBatisPagingItemReader.class));
  }

  /**
   * Public setter for {@link SqlSessionFactory} for injection purposes.
   *
   * @param sqlSessionFactory
   *          a factory object for the {@link SqlSession}.
   */
  public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }

  /**
   * Public setter for the statement id identifying the statement in the SqlMap configuration file.
   *
   * @param queryId
   *          the id for the statement
   */
  public void setQueryId(String queryId) {
    this.queryId = queryId;
  }

  /**
   * The parameter values to be used for the query execution.
   *
   * @param parameterValues
   *          the values keyed by the parameter named used in the query string.
   */
  public void setParameterValues(Map<String, Object> parameterValues) {
    this.parameterValues = parameterValues;
  }

  /**
   * The parameter supplier used to get parameter values for the query execution.
   *
   * @param parameterValuesSupplier
   *          the supplier used to get values keyed by the parameter named used in the query string.
   *
   * @since 2.1.0
   */
  public void setParameterValuesSupplier(Supplier<Map<String, Object>> parameterValuesSupplier) {
    this.parameterValuesSupplier = parameterValuesSupplier;
  }

  /**
   * Check mandatory properties.
   *
   * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
   */
  @Override
  public void afterPropertiesSet() throws Exception {
    // 父类的处理
    super.afterPropertiesSet();
    notNull(sqlSessionFactory, "A SqlSessionFactory is required.");
    notNull(queryId, "A queryId is required.");
  }

  @Override
  protected void doReadPage() {
    if (sqlSessionTemplate == null) { // 如果SqlSessionTemplate为null，则创建 SqlSessionTemplate 对象
      sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory, ExecutorType.BATCH);
    }
    // 创建 parameters 参数
    Map<String, Object> parameters = new HashMap<>();
    if (parameterValues != null) {  // 设置原有参数
      parameters.putAll(parameterValues);
    }
    // 设置分页参数
    Optional.ofNullable(parameterValuesSupplier).map(Supplier::get).ifPresent(parameters::putAll);
    parameters.put("_page", getPage());
    parameters.put("_pagesize", getPageSize());
    parameters.put("_skiprows", getPage() * getPageSize());
    // 清空目前的 results 结果 保证是空的数组
    if (results == null) {
      // 使用 CopyOnWriteArrayList 的原因是，可能存在并发读取的问题
      results = new CopyOnWriteArrayList<>();
    } else {
      results.clear();
    }
    // 执行查询列表。查询后，将结果添加到 results 中
    results.addAll(sqlSessionTemplate.selectList(queryId, parameters));
  }

}
