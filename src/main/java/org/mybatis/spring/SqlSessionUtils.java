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
package org.mybatis.spring;

import static org.springframework.util.Assert.notNull;

import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.logging.Logger;
import org.mybatis.logging.LoggerFactory;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * SqlSession 工具类。
 * 它负责处理 MyBatis SqlSession 的生命周期。它可以从 Spring TransactionSynchronizationManager 中，
 * 注册和获得对应的 SqlSession 对象。同时，它也支持当前不处于事务的情况下。
 * Handles MyBatis SqlSession life cycle. It can register and get SqlSessions from Spring
 * {@code TransactionSynchronizationManager}. Also works if no transaction is active.
 *
 * @author Hunter Presnall
 * @author Eduardo Macarron
 */
public final class SqlSessionUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(SqlSessionUtils.class);

  private static final String NO_EXECUTOR_TYPE_SPECIFIED = "No ExecutorType specified";
  private static final String NO_SQL_SESSION_FACTORY_SPECIFIED = "No SqlSessionFactory specified";
  private static final String NO_SQL_SESSION_SPECIFIED = "No SqlSession specified";

  /**
   * 空参构造器
   * This class can't be instantiated, exposes static utility methods only.
   */
  private SqlSessionUtils() {
    // do nothing
  }

  /**
   * 获得 SqlSession 对象
   * Creates a new MyBatis {@code SqlSession} from the {@code SqlSessionFactory} provided as a parameter and using its
   * {@code DataSource} and {@code ExecutorType}
   * 从作为参数提供的{@code SqlSessionFactory}中创建一个新的MyBatis {@code SqlSession}，并使用它的{@code DataSource}和{@code ExecutorType}
   *
   * @param sessionFactory
   *          a MyBatis {@code SqlSessionFactory} to create new sessions
   *
   * @return a MyBatis {@code SqlSession}
   *
   * @throws TransientDataAccessResourceException
   *           if a transaction is active and the {@code SqlSessionFactory} is not using a
   *           {@code SpringManagedTransactionFactory}
   */
  public static SqlSession getSqlSession(SqlSessionFactory sessionFactory) {
    // 获得执行器类型
    var executorType = sessionFactory.getConfiguration().getDefaultExecutorType();
    // 获得 SqlSession 对象
    return getSqlSession(sessionFactory, executorType, null);
  }

  /**
   * Gets an SqlSession from Spring Transaction Manager or creates a new one if needed. Tries to get a SqlSession out of
   * current transaction. If there is not any, it creates a new one. Then, it synchronizes the SqlSession with the
   * transaction if Spring TX is active and <code>SpringManagedTransactionFactory</code> is configured as a transaction
   * manager.
   *
   * @param sessionFactory
   *          a MyBatis {@code SqlSessionFactory} to create new sessions
   * @param executorType
   *          The executor type of the SqlSession to create
   * @param exceptionTranslator
   *          Optional. Translates SqlSession.commit() exceptions to Spring exceptions.
   *
   * @return an SqlSession managed by Spring Transaction Manager
   *
   * @throws TransientDataAccessResourceException
   *           if a transaction is active and the {@code SqlSessionFactory} is not using a
   *           {@code SpringManagedTransactionFactory}
   *
   * @see SpringManagedTransactionFactory
   */
  public static SqlSession getSqlSession(SqlSessionFactory sessionFactory, ExecutorType executorType,
      PersistenceExceptionTranslator exceptionTranslator) {

    notNull(sessionFactory, NO_SQL_SESSION_FACTORY_SPECIFIED);
    notNull(executorType, NO_EXECUTOR_TYPE_SPECIFIED);

    // 通过 TransactionSynchronizationManager 获得 SqlSessionHolder 对象
    // 如果获取不到，会在下面进行注册，即registerSessionHolder(sessionFactory, executorType, exceptionTranslator, session);
    var holder = (SqlSessionHolder) TransactionSynchronizationManager.getResource(sessionFactory);

    // 通过sessionHolder方法，获得 SqlSession 对象
    var session = sessionHolder(executorType, holder);
    if (session != null) {  // 如果非空，直接返回
      return session;
    }

    LOGGER.debug(() -> "Creating a new SqlSession");
    // 创建 SqlSession 对象
    session = sessionFactory.openSession(executorType);

    // 注册 SqlSession 对象，到 TransactionSynchronizationManager 中
    registerSessionHolder(sessionFactory, executorType, exceptionTranslator, session);

    return session;
  }

  /**
   * Register session holder if synchronization is active (i.e. a Spring TX is active).
   * <p>
   * Note: The DataSource used by the Environment should be synchronized with the transaction either through
   * DataSourceTxMgr or another tx synchronization. Further assume that if an exception is thrown, whatever started the
   * transaction will handle closing / rolling back the Connection associated with the SqlSession.
   *
   * @param sessionFactory
   *          sqlSessionFactory used for registration.
   * @param executorType
   *          executorType used for registration.
   * @param exceptionTranslator
   *          persistenceExceptionTranslator used for registration.
   * @param session
   *          sqlSession used for registration.
   */
  private static void registerSessionHolder(SqlSessionFactory sessionFactory, ExecutorType executorType,
      PersistenceExceptionTranslator exceptionTranslator, SqlSession session) {
    SqlSessionHolder holder;
    // 如果使用 Spring 事务管理器
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      var environment = sessionFactory.getConfiguration().getEnvironment();

      if (environment.getTransactionFactory() instanceof SpringManagedTransactionFactory) {
        LOGGER.debug(() -> "Registering transaction synchronization for SqlSession [" + session + "]");

        // 创建 SqlSessionHolder 对象
        holder = new SqlSessionHolder(session, executorType, exceptionTranslator);
        // 绑定到 TransactionSynchronizationManager 中
        TransactionSynchronizationManager.bindResource(sessionFactory, holder);
        // 创建 SqlSessionSynchronization 到 TransactionSynchronizationManager 中
        TransactionSynchronizationManager
            .registerSynchronization(new SqlSessionSynchronization(holder, sessionFactory));
        // 设置同步
        holder.setSynchronizedWithTransaction(true);
        // 增加计数
        holder.requested();
      } else if (TransactionSynchronizationManager.getResource(environment.getDataSource()) == null) { // 如果非 Spring 事务管理器，抛出 TransientDataAccessResourceException 异常
        LOGGER.debug(() -> "SqlSession [" + session
            + "] was not registered for synchronization because DataSource is not transactional");
      } else {
        throw new TransientDataAccessResourceException(
            "SqlSessionFactory must be using a SpringManagedTransactionFactory in order to use Spring transaction synchronization");
      }
    } else {
      LOGGER.debug(() -> "SqlSession [" + session
          + "] was not registered for synchronization because synchronization is not active");
    }

  }

  private static SqlSession sessionHolder(ExecutorType executorType, SqlSessionHolder holder) {
    SqlSession session = null;
    if (holder != null && holder.isSynchronizedWithTransaction()) {
      // 如果执行器类型发生了变更，抛出 TransientDataAccessResourceException 异常
      if (holder.getExecutorType() != executorType) {
        throw new TransientDataAccessResourceException(
            "Cannot change the ExecutorType when there is an existing transaction");
      }

      // 增加计数（用于关闭 SqlSession 时使用） +1
      holder.requested();

      LOGGER.debug(() -> "Fetched SqlSession [" + holder.getSqlSession() + "] from current transaction");
      // 获得 SqlSession 对象
      session = holder.getSqlSession();
    }
    return session;
  }

  /**
   * 关闭 SqlSession 对象
   * Checks if {@code SqlSession} passed as an argument is managed by Spring {@code TransactionSynchronizationManager}
   * If it is not, it closes it, otherwise it just updates the reference counter and lets Spring call the close callback
   * when the managed transaction ends
   *
   * @param session
   *          a target SqlSession
   * @param sessionFactory
   *          a factory of SqlSession
   */
  public static void closeSqlSession(SqlSession session, SqlSessionFactory sessionFactory) {
    notNull(session, NO_SQL_SESSION_SPECIFIED);
    notNull(sessionFactory, NO_SQL_SESSION_FACTORY_SPECIFIED);

    // 从 TransactionSynchronizationManager 中，获得 SqlSessionHolder 对象
    var holder = (SqlSessionHolder) TransactionSynchronizationManager.getResource(sessionFactory);
    //  如果相等，说明在 Spring 托管的事务中，则释放 holder 计数
    if (holder != null && holder.getSqlSession() == session) {
      LOGGER.debug(() -> "Releasing transactional SqlSession [" + session + "]");
      holder.released();
    } else {
      // 如果不相等，说明不在 Spring 托管的事务中，直接关闭 SqlSession 对象
      LOGGER.debug(() -> "Closing non transactional SqlSession [" + session + "]");
      session.close();
    }
  }

  /**
   * 判断传入的 SqlSession 参数，是否在 Spring 事务中
   * Returns if the {@code SqlSession} passed as an argument is being managed by Spring
   *
   * @param session
   *          a MyBatis SqlSession to check
   * @param sessionFactory
   *          the SqlSessionFactory which the SqlSession was built with
   *
   * @return true if session is transactional, otherwise false
   */
  public static boolean isSqlSessionTransactional(SqlSession session, SqlSessionFactory sessionFactory) {
    notNull(session, NO_SQL_SESSION_SPECIFIED);
    notNull(sessionFactory, NO_SQL_SESSION_FACTORY_SPECIFIED);

    // 从 TransactionSynchronizationManager 中，获得 SqlSessionHolder 对象
    var holder = (SqlSessionHolder) TransactionSynchronizationManager.getResource(sessionFactory);

    // 如果相等，说明在 Spring 托管的事务中
    return holder != null && holder.getSqlSession() == session;
  }

  /**
   * SqlSessionSynchronization ，是 SqlSessionUtils 的内部类，
   * 继承 TransactionSynchronizationAdapter 抽象类，SqlSession 的 同步器，基于 Spring Transaction 体系
   * SqlSessionSynchronization ，是 SqlSessionUtils 的内部类，继承 TransactionSynchronizationAdapter 抽象类，SqlSession 的 同步器，基于 Spring Transaction 体系
   * Callback for cleaning up resources. It cleans TransactionSynchronizationManager and also commits and closes the
   * {@code SqlSession}. It assumes that {@code Connection} life cycle will be managed by
   * {@code DataSourceTransactionManager} or {@code JtaTransactionManager}
   */
  private static final class SqlSessionSynchronization implements TransactionSynchronization {

    // SqlSessionHolder 对象
    private final SqlSessionHolder holder;

    // SqlSessionFactory 对象
    private final SqlSessionFactory sessionFactory;

    // 是否开启
    private boolean holderActive = true;

    public SqlSessionSynchronization(SqlSessionHolder holder, SqlSessionFactory sessionFactory) {
      notNull(holder, "Parameter 'holder' must be not null");
      notNull(sessionFactory, "Parameter 'sessionFactory' must be not null");

      this.holder = holder;
      this.sessionFactory = sessionFactory;
    }

    @Override
    public int getOrder() {
      // order right before any Connection synchronization
      return DataSourceUtils.CONNECTION_SYNCHRONIZATION_ORDER - 1;
    }

    // 当事务挂起时，取消当前线程的绑定的 SqlSessionHolder 对象
    @Override
    public void suspend() {
      if (this.holderActive) {
        LOGGER.debug(() -> "Transaction synchronization suspending SqlSession [" + this.holder.getSqlSession() + "]");
        TransactionSynchronizationManager.unbindResource(this.sessionFactory);
      }
    }

    // 当事务恢复时，重新绑定当前线程的 SqlSessionHolder 对象
    @Override
    public void resume() {
      if (this.holderActive) {
        LOGGER.debug(() -> "Transaction synchronization resuming SqlSession [" + this.holder.getSqlSession() + "]");
        TransactionSynchronizationManager.bindResource(this.sessionFactory, this.holder);
      }
    }

    /**
     * ，在事务提交之前，调用 SqlSession#commit() 方法，提交事务。虽然说，Spring 自身也会调用 Connection#commit() 方法，进行事务的提交。
     * 但是，SqlSession#commit() 方法中，不仅仅有事务的提交，还有提交批量操作，刷新本地缓存等等。
     * @param readOnly
     */
    @Override
    public void beforeCommit(boolean readOnly) {
      // Connection commit or rollback will be handled by ConnectionSynchronization or
      // DataSourceTransactionManager.
      // But, do cleanup the SqlSession / Executor, including flushing BATCH statements so
      // they are actually executed.
      // SpringManagedTransaction will no-op the commit over the jdbc connection
      // TODO This updates 2nd level caches but the tx may be rolledback later on!
      if (TransactionSynchronizationManager.isActualTransactionActive()) {
        try {
          LOGGER.debug(() -> "Transaction synchronization committing SqlSession [" + this.holder.getSqlSession() + "]");
          // 提交事务
          this.holder.getSqlSession().commit();
        } catch (PersistenceException p) { // 如果发生异常，则进行转换，并抛出异常
          if (this.holder.getPersistenceExceptionTranslator() != null) {
            var translated = this.holder.getPersistenceExceptionTranslator().translateExceptionIfPossible(p);
            if (translated != null) {
              throw translated;
            }
          }
          throw p;
        }
      }
    }

    // 提交事务完成之前，关闭 SqlSession 对象
    @Override
    public void beforeCompletion() {
      // Issue #18 Close SqlSession and deregister it now
      // because afterCompletion may be called from a different thread
      if (!this.holder.isOpen()) {
        LOGGER
            .debug(() -> "Transaction synchronization deregistering SqlSession [" + this.holder.getSqlSession() + "]");
        // 取消当前线程的绑定的 SqlSessionHolder 对象
        TransactionSynchronizationManager.unbindResource(sessionFactory);
        // 标记无效
        this.holderActive = false;
        LOGGER.debug(() -> "Transaction synchronization closing SqlSession [" + this.holder.getSqlSession() + "]");
        // 关闭 SqlSession 对象
        this.holder.getSqlSession().close();
      }
    }

    // 解决可能出现的跨线程的情况，
    @Override
    public void afterCompletion(int status) {
      if (this.holderActive) {
        // afterCompletion may have been called from a different thread
        // so avoid failing if there is nothing in this one
        LOGGER
            .debug(() -> "Transaction synchronization deregistering SqlSession [" + this.holder.getSqlSession() + "]");
        // 取消当前线程的绑定的 SqlSessionHolder 对象
        TransactionSynchronizationManager.unbindResourceIfPossible(sessionFactory);
        // 标记无效
        this.holderActive = false;
        LOGGER.debug(() -> "Transaction synchronization closing SqlSession [" + this.holder.getSqlSession() + "]");
        // 关闭 SqlSession 对象
        this.holder.getSqlSession().close();
      }
      this.holder.reset();
    }
  }

}
