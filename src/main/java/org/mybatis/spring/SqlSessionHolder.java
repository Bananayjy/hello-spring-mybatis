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
package org.mybatis.spring;

import static org.springframework.util.Assert.notNull;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.transaction.support.ResourceHolderSupport;

/**
 * SqlSession 持有器
 * 用于保存当前 SqlSession 对象，保存到 org.springframework.transaction.support.TransactionSynchronizationManager 中
 * 关于TransactionSynchronizationManager 是 Spring 框架中一个重要的工具类，主要用于管理事务同步资源和事务同步回调
 *
 * Used to keep current {@code SqlSession} in {@code TransactionSynchronizationManager}. The {@code SqlSessionFactory}
 * that created that {@code SqlSession} is used as a key. {@code ExecutorType} is also kept to be able to check if the
 * user is trying to change it during a TX (that is not allowed) and throw a Exception in that case.
 * 用于保持当前{@code SqlSession}在{@code TransactionSynchronizationManager}。创建{@code SqlSession}的{@code SqlSessionFactory}被用作键。
 * {@code ExecutorType}也保持能够检查用户是否试图在TX期间更改它（这是不允许的），并在这种情况下抛出异常。
 *
 * @author Hunter Presnall
 * @author Eduardo Macarron
 */
public final class SqlSessionHolder extends ResourceHolderSupport {

  // SqlSession 对象
  private final SqlSession sqlSession;

  // 执行器类型
  private final ExecutorType executorType;

  // PersistenceExceptionTranslator 对象（用于异常转换）
  private final PersistenceExceptionTranslator exceptionTranslator;

  /**
   * Creates a new holder instance.
   * 创建一个SqlSessionHolder实例
   *
   * @param sqlSession
   *          the {@code SqlSession} has to be hold.
   * @param executorType
   *          the {@code ExecutorType} has to be hold.
   * @param exceptionTranslator
   *          the {@code PersistenceExceptionTranslator} has to be hold.
   */
  public SqlSessionHolder(SqlSession sqlSession, ExecutorType executorType,
      PersistenceExceptionTranslator exceptionTranslator) {

    notNull(sqlSession, "SqlSession must not be null");
    notNull(executorType, "ExecutorType must not be null");

    this.sqlSession = sqlSession;
    this.executorType = executorType;
    this.exceptionTranslator = exceptionTranslator;
  }

  public SqlSession getSqlSession() {
    return sqlSession;
  }

  public ExecutorType getExecutorType() {
    return executorType;
  }

  public PersistenceExceptionTranslator getPersistenceExceptionTranslator() {
    return exceptionTranslator;
  }

}
