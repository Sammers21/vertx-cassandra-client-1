/*
 * Copyright 2018 The Vert.x Community.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.vertx.cassandra.impl;

import com.datastax.driver.core.ColumnDefinitions;
import com.datastax.driver.core.Row;
import io.vertx.cassandra.ResultSet;
import io.vertx.core.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.vertx.cassandra.impl.Util.handleOnContext;

/**
 * @author Pavel Drankou
 * @author Thomas Segismont
 */
public class ResultSetImpl implements ResultSet {

  private com.datastax.driver.core.ResultSet resultSet;
  private Vertx vertx;

  public ResultSetImpl(com.datastax.driver.core.ResultSet resultSet, Vertx vertx) {
    this.resultSet = resultSet;
    this.vertx = vertx;
  }

  @Override
  public boolean isExhausted() {
    return resultSet.isExhausted();
  }

  @Override
  public boolean isFullyFetched() {
    return resultSet.isFullyFetched();
  }

  @Override
  public int getAvailableWithoutFetching() {
    return resultSet.getAvailableWithoutFetching();
  }

  @Override
  public ResultSet fetchMoreResults(Handler<AsyncResult<Void>> handler) {
    Context context = vertx.getOrCreateContext();
    handleOnContext(resultSet.fetchMoreResults(), context, ignore -> null, handler);
    return this;
  }

  @Override
  public ResultSet one(Handler<AsyncResult<Row>> handler) {
    if (getAvailableWithoutFetching() == 0 && !resultSet.isFullyFetched()) {
      Context context = vertx.getOrCreateContext();
      handleOnContext(resultSet.fetchMoreResults(), context, ignored -> resultSet.one(), handler);
    } else {
      handler.handle(Future.succeededFuture(resultSet.one()));
    }
    return this;
  }

  @Override
  public ResultSet several(int amount, Handler<AsyncResult<List<Row>>> handler) {
    loadFew(amount, handler);
    return this;
  }

  private void loadFew(int amount, Handler<AsyncResult<List<Row>>> handler) {
    if (isFullyFetched()) {
      if (getAvailableWithoutFetching() >= amount) {
        List<Row> rows = getRows(amount);
        handler.handle(Future.succeededFuture(rows));
      } else {
        int amountToFetch = getAvailableWithoutFetching();
        List<Row> rows = getRows(amountToFetch);
        handler.handle(Future.succeededFuture(rows));
      }
    } else if (!isFullyFetched()) {
      fetchMoreResults(voidAsyncResult -> loadFew(amount, handler));
    }
  }

  private List<Row> getRows(int amountToFetch) {
    List<Row> rows = new ArrayList<>(amountToFetch);
    for (int i = 0; i < amountToFetch; i++) {
      rows.add(resultSet.one());
    }
    return rows;
  }

  @Override
  public ResultSet all(Handler<AsyncResult<List<Row>>> handler) {
    loadMore(vertx.getOrCreateContext(), Collections.emptyList(), handler);
    return this;
  }

  private void loadMore(Context context, List<Row> loaded, Handler<AsyncResult<List<Row>>> handler) {
    int availableWithoutFetching = resultSet.getAvailableWithoutFetching();
    List<Row> rows = new ArrayList<>(loaded.size() + availableWithoutFetching);
    for (int i = 0; i < availableWithoutFetching; i++) {
      rows.add(resultSet.one());
    }

    if (!resultSet.isFullyFetched()) {
      handleOnContext(resultSet.fetchMoreResults(), context, ar -> {
        if (ar.succeeded()) {
          loadMore(context, rows, handler);
        } else {
          handler.handle(Future.failedFuture(ar.cause()));
        }
      });
    } else {
      handler.handle(Future.succeededFuture(rows));
    }
  }

  @Override
  public ColumnDefinitions getColumnDefinitions() {
    return resultSet.getColumnDefinitions();
  }

  @Override
  public boolean wasApplied() {
    return resultSet.wasApplied();
  }
}
