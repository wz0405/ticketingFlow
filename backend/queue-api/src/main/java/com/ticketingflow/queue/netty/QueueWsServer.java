package com.ticketingflow.queue.netty;

import com.ticketingflow.queue.config.QueueProps;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 대기열 순번 푸시용 Netty WebSocket 서버.
 * 폴링(read|queueStatus)의 푸시 대체 경로 — 접속이 많을수록 폴링 대비 유리하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueWsServer implements SmartLifecycle {

    public static final String WS_PATH = "/ws/queue";

    private final QueueProps props;
    private final WsSessionRegistry registry;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private volatile boolean running;

    @Override
    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(65536))
                                .addLast(new QueueWsHandler(registry))
                                .addLast(new WebSocketServerProtocolHandler(WS_PATH, null, true));
                    }
                });
        bootstrap.bind(props.wsPort()).syncUninterruptibly();
        running = true;
        log.info("queue ws server started on port {}", props.wsPort());
    }

    @Override
    public void stop() {
        running = false;
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        log.info("queue ws server stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
