package com.redhat.demo;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;

public class DatabaseVerticle extends AbstractVerticle {

  public static final String LIST_ALL_TODOS_ADDR = "com.redhat.demo.vertx.list_all_todos";
  public static final String ADD_TODO_ADD = "com.redhat.demo.vertx.add_todo";
  public static final String GET_TODO_BY_ID_ADDR = "com.redhat.demo.vertx.get_todo_by_id";
  public static final String UPDATE_TODO_ADDR = "com.redhat.demo.vertx.update_todo";

  private static final String LIST_ALL_TODOS = "SELECT * FROM todos ORDER BY created ASC";
  private static final String GET_TODO_BY_ID = "SELECT * FROM todos WHERE id = ?";
  private static final String UPDATE_TODO = "UPDATE todos SET title = ?, description = ?, due_date = ?, complete = ? WHERE id = ? RETURNING *";
  private static final String ADD_TODO = "INSERT INTO todos (title, description, due_date, complete) VALUES (?, ?, ?, ?) RETURNING *";

  SQLClient client;

  @Override
  public void start(Promise<Void> start) throws Exception {
    doDatabaseMigrations()
      .compose(this::configureSqlClient)
      .compose(this::configureEventBusConsumers)
      .setHandler(start::handle);
  }

  Future<Void> configureEventBusConsumers(Void unused) {
    vertx.eventBus().consumer(LIST_ALL_TODOS_ADDR).handler(this::listAllTodos);
    vertx.eventBus().consumer(GET_TODO_BY_ID_ADDR).handler(this::getTodoById);
    vertx.eventBus().consumer(UPDATE_TODO_ADDR).handler(this::updateTodo);
    vertx.eventBus().consumer(ADD_TODO_ADD).handler(this::addTodo);

    return Promise.<Void>succeededPromise().future();
  }

  void updateTodo(Message<Object> msg) {
    if (msg.body() instanceof JsonObject) {
      JsonObject todo = (JsonObject)msg.body();
      JsonArray params = new JsonArray()
        .add(todo.getString("title"))
        .add(todo.getString("description", ""))
        .add(todo.getString("due_date", null))
        .add(todo.getBoolean("complete", false))
        .add(todo.getString("id"));
      Future.<SQLConnection>future(client::getConnection)
        .compose(conn -> this.queryWithParamters(conn, UPDATE_TODO, params))
        .compose(this::mapToFirstResult)
        .setHandler(res -> {
          if (res.succeeded()) {
            msg.reply(res.result());
          } else {
            msg.fail(500, res.cause().getLocalizedMessage());
          }
        });
    } else {
      msg.fail(400, "Bad Request: You must supply a valid Todo item in the body of the request");
    }
  }

  void addTodo(Message<Object> msg) {
    if (msg.body() instanceof JsonObject) {
      JsonObject todo = (JsonObject)msg.body();
      if (todo.containsKey("title")) {
        JsonArray params = new JsonArray()
          .add(todo.getString("title"))
          .add(todo.getString("description", ""))
          .add(todo.getBoolean("complete", false))
          .add(todo.getString("due_date", null));
        Future.<SQLConnection>future(client::getConnection)
          .compose(conn -> this.queryWithParamters(conn, ADD_TODO, params))
          .compose(this::mapToFirstResult)
          .setHandler(res -> {
            if (res.succeeded()) {
              msg.reply(res.result());
            } else {
              msg.fail(500, res.cause().getLocalizedMessage());
            }
          });
      } else {
        msg.fail(400, "Bad Request: Required field 'title' missing from todo item.");
      }
    } else {
      msg.fail(400, "Bad Request: You must supply a valid Todo item in the body of the request");
    }
  }

  void getTodoById(Message<Object> msg) {
    if (msg.body() instanceof String) {
      String id = (String)msg.body();
      JsonArray params = new JsonArray().add(id);
      Future.<SQLConnection>future(client::getConnection)
        .compose(conn -> this.queryWithParamters(conn, GET_TODO_BY_ID, params))
        .compose(this::mapToFirstResult)
        .setHandler(res -> {
          if (res.succeeded()) {
            msg.reply(res.result());
          } else {
            msg.fail(500, res.cause().getLocalizedMessage());
          }
        });
    } else {
      msg.fail(400, "Bad Request, you must supply the Todo ID");
    }
  }

  void listAllTodos(Message<Object> msg) {
    Future.<SQLConnection>future(client::getConnection)
      .compose(conn -> this.queryWithParamters(conn, LIST_ALL_TODOS, new JsonArray()))
      .compose(this::mapToJsonArray)
      .setHandler(res -> {
        if (res.succeeded()) {
          msg.reply(res.result());
        } else {
          msg.fail(500, res.cause().getLocalizedMessage());
        }
      });
  }

  Future<Void> configureSqlClient(Void unused) {
    client = JDBCClient.createShared(vertx, config().getJsonObject("db"));
    return Promise.<Void>succeededPromise().future();
  }

  Future<ResultSet> queryWithParamters(SQLConnection conn, String query, JsonArray params) {
    return Future.<ResultSet>future(promise -> conn.queryWithParams(query, params, promise));
  }

  Future<JsonObject> mapToFirstResult(ResultSet rs) {
    if (rs.getNumRows() >= 1) {
      return Promise.succeededPromise(rs.getRows().get(0)).future();
    } else {
      return Promise.<JsonObject>failedPromise("No results").future();
    }
  }

  Future<JsonArray> mapToJsonArray(ResultSet rs) {
    return Promise.succeededPromise(new JsonArray(rs.getRows())).future();
  }

  /**
   * Uses the {@code loadedConfig} to try to perform the required database schema migrations
   * @param unused A {@link Void} object which is ignored
   * @return A {@link Future} which indicates the success/failure of this operation
   */
  Future<Void> doDatabaseMigrations() {
      JsonObject dbConfig = config().getJsonObject("db", new JsonObject());
      String url = dbConfig.getString("url", "jdbc:postgresql://127.0.0.1:5432/todo");
      String adminUser = dbConfig.getString("admin_user", "postgres");
      String adminPass = dbConfig.getString("admin_pass", "introduction");
      Flyway flyway = Flyway.configure().dataSource(url, adminUser, adminPass).load();

      try {
          flyway.migrate();
          return Promise.<Void>succeededPromise().future();
      } catch (FlywayException fe) {
          return Promise.<Void>failedPromise(fe).future();
      }
  }
}