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
package org.mybatis.spring.batch;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.batch.domain.Employee;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

@SpringJUnitConfig(locations = { "classpath:org/mybatis/spring/batch/applicationContext.xml" })
class SpringBatchTest {

  @Autowired
  @Qualifier("pagingNoNestedItemReader")
  private MyBatisPagingItemReader<Employee> pagingNoNestedItemReader;

  @Autowired
  @Qualifier("pagingNestedItemReader")
  private MyBatisPagingItemReader<Employee> pagingNestedItemReader;

  @Autowired
  @Qualifier("cursorNoNestedItemReader")
  private MyBatisCursorItemReader<Employee> cursorNoNestedItemReader;

  @Autowired
  @Qualifier("cursorNestedItemReader")
  private MyBatisCursorItemReader<Employee> cursorNestedItemReader;

  @Autowired
  private MyBatisBatchItemWriter<Employee> writer;

  @Autowired
  private SqlSession session;

  @Test
  @Transactional
  void shouldDuplicateSalaryOfAllEmployees() throws Exception {
    var employees = new Chunk<Employee>();
    // 批量读取 Employee 数组
    var employee = pagingNoNestedItemReader.read();
    while (employee != null) { // 不断读取，直到为空
      employee.setSalary(employee.getSalary() * 2);
      employees.add(employee);
      employee = pagingNoNestedItemReader.read();
    }
    // 批量写入
    writer.write(employees);

    assertThat((Integer) session.selectOne("checkSalarySum")).isEqualTo(20000);
    assertThat((Integer) session.selectOne("checkEmployeeCount")).isEqualTo(employees.size());
  }

  @Test
  @Transactional
  void checkPagingReadingWithNestedInResultMap() throws Exception {
    // This test is here to show that PagingReader can return wrong result in case of nested result maps
    var employees = new Chunk<Employee>();
    var employee = pagingNestedItemReader.read();
    while (employee != null) {
      employee.setSalary(employee.getSalary() * 2);
      employees.add(employee);
      employee = pagingNestedItemReader.read();
    }
    writer.write(employees);

    // Assert that we have a WRONG employee count
    assertThat((Integer) session.selectOne("checkEmployeeCount")).isNotEqualTo(employees.size());
  }

  @Test
  @Transactional
  void checkCursorReadingWithoutNestedInResultMap() throws Exception {
    // 打开 Cursor
    cursorNoNestedItemReader.doOpen();
    try {
      // Employee 数组
      var employees = new Chunk<Employee>();
      // 循环读取，写入到 Employee 数组中
      var employee = cursorNoNestedItemReader.read();
      while (employee != null) {
        employee.setSalary(employee.getSalary() * 2);
        employees.add(employee);
        employee = cursorNoNestedItemReader.read();
      }
      // 批量写入
      writer.write(employees);

      assertThat((Integer) session.selectOne("checkSalarySum")).isEqualTo(20000);
      assertThat((Integer) session.selectOne("checkEmployeeCount")).isEqualTo(employees.size());
    } finally {
      // 关闭 Cursor
      cursorNoNestedItemReader.doClose();
    }
  }

  @Test
  @Transactional
  void checkCursorReadingWithNestedInResultMap() throws Exception {
    cursorNestedItemReader.doOpen();
    try {
      var employees = new Chunk<Employee>();
      var employee = cursorNestedItemReader.read();
      while (employee != null) {
        employee.setSalary(employee.getSalary() * 2);
        employees.add(employee);
        employee = cursorNestedItemReader.read();
      }
      writer.write(employees);

      assertThat((Integer) session.selectOne("checkSalarySum")).isEqualTo(20000);
      assertThat((Integer) session.selectOne("checkEmployeeCount")).isEqualTo(employees.size());
    } finally {
      cursorNestedItemReader.doClose();
    }
  }
}
