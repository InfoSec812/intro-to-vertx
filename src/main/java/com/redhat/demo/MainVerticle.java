package com.redhat.demo;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class MainVerticle extends AbstractVerticle {

    final JsonObject loadedConfig = new JsonObject();

    @Override
    public void start(Promise<Void> start) {
        // Sequential Composition - Do A, Then B, Then C . . . . Handle errors
        // https://vertx.io/docs/vertx-core/java/#_sequential_composition
        doConfig()
            .compose(this::storeConfig)
            .compose(this::deployOtherVerticles)
            .setHandler(start::handle);
    }

    /**
     * Set up and execute the {@link ConfigRetriever} to load the configuration for the application
     * @param start The {@link Promise} which is to be resolved as this Verticle loads
     * @param router The {@link Router} for the REST API paths
     */
    Future<JsonObject> doConfig() {
        ConfigStoreOptions defaultConfig = new ConfigStoreOptions()
                .setType("file")
                .setFormat("json")
                .setConfig(new JsonObject().put("path", "config.json"));
        ConfigStoreOptions cliConfig = new ConfigStoreOptions()
                .setType("json")
                .setConfig(config());

        ConfigRetrieverOptions opts = new ConfigRetrieverOptions()
                .addStore(defaultConfig)
                .addStore(cliConfig);

        ConfigRetriever cfgRetriever = ConfigRetriever.create(vertx, opts);

        return Future.future(promise -> cfgRetriever.getConfig(promise));
    }

    /**
     * Store loaded configuration for use in subsequent operations
     * @param config The configuration loaded via Vert.x Config
     * @return A {@link Future} of type {@link Void} indication the success or failure of this operation
     */
    Future<Void> storeConfig(JsonObject config) {
        loadedConfig.mergeIn(config);
        return Promise.<Void>succeededPromise().future();
    }

    /**
     * Deploy our other {@link io.vertx.core.Verticle}s in concurrently
     * https://vertx.io/docs/vertx-core/java/#_concurrent_composition
     * @param unused A {@link Void} instance (Not used in this method)
     * @return A {@link Future} which is resolved once both of the Verticles are deployed
     */
    Future<Void> deployOtherVerticles(Void unused) {
        DeploymentOptions opts = new DeploymentOptions().setConfig(loadedConfig);

        Future<String> dbVerticle = Future.future(promise -> vertx.deployVerticle(new DatabaseVerticle(), opts, promise));
        Future<String> webVerticle = Future.future(promise -> vertx.deployVerticle(new WebVerticle(), opts, promise));
        Future<String> helloGroovy = Future.future(promise -> vertx.deployVerticle("Hello.groovy", opts, promise));
        Future<String> helloJs = Future.future(promise -> vertx.deployVerticle("Hello.js", opts, promise));

        return CompositeFuture.all(helloGroovy, helloJs, dbVerticle, webVerticle).mapEmpty();
    }
}
