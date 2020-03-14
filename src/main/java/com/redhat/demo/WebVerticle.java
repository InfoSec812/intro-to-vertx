package com.redhat.demo;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CSRFHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.sstore.SessionStore;

public class WebVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> start) throws Exception {
    configureRouter()
      .compose(this::startHttpServer)
      .setHandler(start::handle);
  }

  /**
   * Configures the {@link Router} for use in handling HTTP requests in the server
   * @param unused A {@link Void} parameter which is ignored
   * @return A {@link Future}, potentially containing the {@link Router}, if this succeeds
   */
  Future<Router> configureRouter() {
      Router router = Router.router(vertx);

      SessionStore store = LocalSessionStore.create(vertx);
      router.route().handler(LoggerHandler.create());
      router.route().handler(SessionHandler.create(store));
      router.route().handler(CorsHandler.create("localhost"));
      router.route().handler(CSRFHandler.create("QBR2QTlCvBaAugUBYdd6uWHkx4qA5yaVyxX/GyIgX0xwD71U1KamTWfyBmSgt3VHefeaNrdqdbvh"));
      router.get("/api/v1/hello").handler(this::helloHandler);
      router.get("/api/v1/hello/:name").handler(this::helloByNameHandler);
      router.route().handler(StaticHandler.create("web"));

      return Promise.succeededPromise(router).future();
  }

  /**
   * Using the provided {@link Router}, start and {@link HttpServer} and use the router as the handler
   * @param A {@link Router} configured in the previous method
   * @return A {@link Future} which will contain the {@link HttpServer} on successful creation of the server
   */
  Future<Void> startHttpServer(Router router) {
      JsonObject http = config().getJsonObject("http");
      int httpPort = http.getInteger("port");
      HttpServer server = vertx.createHttpServer().requestHandler(router);

      return Future.<HttpServer>future(promise -> server.listen(httpPort, promise)).mapEmpty();
  }

  /**
   * A handler for requests to the `/api/v1/hello` REST endpoint
   * @param ctx The {@link RoutingContext} of the request
   */
  void helloHandler(RoutingContext ctx) {
      vertx.eventBus().request("hello.vertx.addr", "", reply -> {
          ctx.request().response().end((String)reply.result().body());
      });
  }

  /**
   * A handler for requests to the `/api/v1/hello/:name` REST endpoint
   * @param ctx The {@link RoutingContext} of the request
   */
  void helloByNameHandler(RoutingContext ctx) {
      String name = ctx.pathParam("name");
      vertx.eventBus().request("hello.named.addr", name, reply -> {
          ctx.request().response().end((String)reply.result().body());
      });
  }
}