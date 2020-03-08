vertx.eventBus().consumer("hello.vertx.addr", function(msg) {
  msg.reply("Hello Vert.x World from JavaScript!");
});