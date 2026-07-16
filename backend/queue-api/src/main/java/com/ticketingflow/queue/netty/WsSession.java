package com.ticketingflow.queue.netty;

import io.netty.channel.Channel;

/**
 * WS 접속 1건 = (회차, 사용자, 채널).
 */
public record WsSession(String schdNo, String usrId, Channel channel) {
}
