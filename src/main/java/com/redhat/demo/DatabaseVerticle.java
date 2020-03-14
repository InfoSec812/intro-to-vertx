package com.redhat.demo;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

public class DatabaseVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> start) throws Exception {
    doDatabaseMigrations()
      .setHandler(start::handle);
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