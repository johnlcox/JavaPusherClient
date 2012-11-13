package com.justinschultz.pusherclient;

public interface PusherAuthorizor {
	public String authorize(String socketId, String channel);
}
