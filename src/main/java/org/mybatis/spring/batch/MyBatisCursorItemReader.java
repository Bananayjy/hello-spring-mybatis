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
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.batch.item.support.AbstractItemCountingItemStreamItemReader;
import org.springframework.beans.factory.InitializingBean;

/**
 * 基于 Cursor 的 MyBatis 的读取器
 * 继承 org.springframework.batch.item.support.AbstractItemCountingItemStreamItemReader 抽象类，实现 InitializingBean 接口
 * @author Guillaume Darmont / guillaume@dropinocean.com
 */
public class MyBatisCursorItemReader<T> extends AbstractItemCountingItemStreamItemReader<T>
    implements InitializingBean {

  // 查询编号
  private String queryId;

  //  SqlSessionFactory 对象
  private SqlSessionFactory sqlSessionFactory;

  // SqlSession 对象
  private SqlSession sqlSession;

  // 参数值的映射
  private Map<String, Object> parameterValues;
  private Supplier<Map<String, Object>> parameterValuesSupplier;

  private Cursor<T> cursor;
  private Iterator<T> cursorIterator;

  public MyBatisCursorItemReader() {
    setName(getShortName(MyBatisCursorItemReader.class));
  }

  @Override
  protected T doRead() throws Exception {
    // 置空 next
    T next = null;
    // 读取下一条
    if (cursorIterator.hasNext()) {
      next = cursorIterator.next();
    }
    // 返回
    return next;
  }

  // 打开 Cursor
  @Override
  protected void doOpen() throws Exception {
    // 创建 parameters 参数
    Map<String, Object> parameters = new HashMap<>();
    if (parameterValues != null) {
      parameters.putAll(parameterValues);
    }

    Optional.ofNullable(parameterValuesSupplier).map(Supplier::get).ifPresent(parameters::putAll);

    // 创建 SqlSession 对象
    sqlSession = sqlSessionFactory.openSession(ExecutorType.SIMPLE);
    // 查询，返回 Cursor 对象
    cursor = sqlSession.selectCursor(queryId, parameters);
    // 获得 cursor 的迭代器
    cursorIterator = cursor.iterator();
  }

  // 关闭 Cursor 和 SqlSession 对象
  @Override
  protected void doClose() throws Exception {
    // 关闭 cursor 对象
    if (cursor != null) {
      cursor.close();
    }
    // 关闭 sqlSession 对象
    if (sqlSession != null) {
      sqlSession.close();
    }
    // 置空 cursorIterator
    cursorIterator = null;
  }

  /**
   * Check mandatory properties.
   *
   * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
   */
  @Override
  public void afterPropertiesSet() throws Exception {
    notNull(sqlSessionFactory, "A SqlSessionFactory is required.");
    notNull(queryId, "A queryId is required.");
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
}
