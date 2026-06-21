package com.im.connect.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Netty WebSocket 服务，随 Spring 启动而绑定端口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NettyWebSocketServer {

    @Value("${im.netty.port}")
    private int port;
    @Value("${im.netty.path}")
    private String path;
    @Value("${im.netty.heartbeat-seconds:60}")
    private int heartbeatSeconds;

    private final WebSocketFrameHandler frameHandler;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    @PostConstruct
    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new HttpServerCodec());
                        p.addLast(new ChunkedWriteHandler());
                        p.addLast(new HttpObjectAggregator(64 * 1024));
                        p.addLast(new WebSocketServerProtocolHandler(path, null, true));
                        // 读空闲超过 heartbeatSeconds 触发 IdleStateEvent，由 frameHandler 关闭死连接
                        p.addLast(new IdleStateHandler(heartbeatSeconds, 0, 0));
                        p.addLast(frameHandler);
                    }
                });
        // 在独立线程绑定，避免阻塞 Spring 启动
        new Thread(() -> {
            try {
                ChannelFuture f = b.bind(port).sync();
                serverChannel = f.channel();
                log.info("Netty WebSocket server started on ws://0.0.0.0:{}{}", port, path);
                serverChannel.closeFuture().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "netty-ws").start();
    }

    @PreDestroy
    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("Netty WebSocket server stopped");
    }
}
