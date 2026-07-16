package com.ticketingflow.queue.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * 핸드셰이크 전 쿼리스트링(schdNo, usrId)을 뽑아 세션으로 등록한다.
 * 파라미터가 없으면 접속을 끊는다.
 */
@Slf4j
@RequiredArgsConstructor
public class QueueWsHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final WsSessionRegistry registry;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        QueryStringDecoder decoder = new QueryStringDecoder(req.uri());
        Map<String, List<String>> params = decoder.parameters();
        String schdNo = first(params, "schdNo");
        String usrId = first(params, "usrId");
        if (schdNo == null || usrId == null) {
            ctx.close();
            return;
        }
        registry.add(new WsSession(schdNo, usrId, ctx.channel()));
        req.setUri(decoder.path());
        ctx.fireChannelRead(req.retain());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            log.debug("ws handshake done: {}", ctx.channel().remoteAddress());
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        registry.remove(ctx.channel());
        ctx.close();
    }

    private String first(Map<String, List<String>> params, String key) {
        List<String> v = params.get(key);
        return v == null || v.isEmpty() ? null : v.get(0);
    }
}
