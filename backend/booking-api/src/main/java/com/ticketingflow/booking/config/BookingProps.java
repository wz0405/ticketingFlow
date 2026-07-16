package com.ticketingflow.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ticketingflow.booking")
public record BookingProps(long holdTtlSec, int maxSeatsPerRsv, int maxQtyPerOrd) {
}
