vertx.eventBus().consumer("hello.named.addr").handler({ msg -> 
  msg.reply("Hello ${msg.body()} from Groovy!")
})