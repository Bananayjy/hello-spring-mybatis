/*
 * Copyright 2010-2024 the original author or authors.
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
package org.mybatis.spring.transaction;

import java.sql.Connection;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.session.TransactionIsolationLevel;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionFactory;

/**
 * SpringManagedTransaction 的工厂实现类（实现 TransactionFactory 接口）
 * mybatis继承Spring管理事务的工厂
 * Creates a {@code SpringManagedTransaction}.
 *
 * @author Hunter Presnall
 */
public class SpringManagedTransactionFactory implements TransactionFactory {

  // 创建 SpringManagedTransaction 对象
  @Override
  public Transaction newTransaction(DataSource dataSource, TransactionIsolationLevel level, boolean autoCommit) {
    // 创建 SpringManagedTransaction 对象
    return new SpringManagedTransaction(dataSource);
  }

  // 创建 SpringManagedTransaction 对象
  @Override
  public Transaction newTransaction(Connection conn) {
    // 抛出异常，因为 Spring 事务，需要一个 DataSource 对象
    throw new UnsupportedOperationException("New Spring transactions require a DataSource");
  }

  // 设置属性
  @Override
  public void setProperties(Properties props) {
    // not needed in this version
  }

}
