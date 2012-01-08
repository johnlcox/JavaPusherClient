package com.justinschultz.pusherclient;

/*	
 *  Copyright (C) 2012 Justin Schultz
 *  JavaPusherClient, a Pusher (http://pusherapp.com) client for Java
 *  
 *  http://justinschultz.com/
 *  http://publicstaticdroidmain.com/
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. 
 */

import java.net.URI;
import java.util.HashMap;

import org.json.JSONObject;

import com.justinschultz.websocket.WebSocket;
import com.justinschultz.websocket.WebSocketConnection;
import com.justinschultz.websocket.WebSocketEventHandler;
import com.justinschultz.websocket.WebSocketMessage;

public class Pusher {
	protected static final long PUSHER_SLEEP_TIME_MS = 5000;
	private final String VERSION = "1.8.3";
	private final String HOST = "ws.pusherapp.com";
	private final int WS_PORT = 80;
	private final String PREFIX = "ws://";

	private WebSocket webSocket;
	private Thread pusherThread;
	private String apiKey;
	private final HashMap<String, Channel> channels;

	private PusherListener pusherEventListener;

	public Pusher(String key, PusherListener listener) {
		apiKey = key;
		pusherEventListener = listener;
		channels = new HashMap<String, Channel>();
	}

	public void disconnect() {
		try {
			pusherThread.interrupt();
			pusherThread = null;
			webSocket.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Channel subscribe(String channelName) {
		Channel c = new Channel(channelName);

		if (webSocket != null && webSocket.isConnected()) {
			try {
				sendSubscribeMessage(c);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		channels.put(channelName, c);
		return c;
	}
	
	public Channel subscribe(String channelName, String authToken) {
		Channel c = new Channel(channelName);

		if (webSocket != null && webSocket.isConnected()) {
			try {
				sendPrivateSubscribeMessage(c, authToken);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		channels.put(channelName, c);
		return c;
	}

	public void unsubscribe(String channelName) {
		if (channels.containsKey(channelName)) {
			if (webSocket != null && webSocket.isConnected()) {
				try {
					sendUnsubscribeMessage(channels.get(channelName));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			channels.remove(channelName);
		}
	}

	private void sendSubscribeMessage(Channel c) {
		JSONObject data = new JSONObject();
		c.send("pusher:subscribe", data);
	}
	
	private void sendPrivateSubscribeMessage(Channel c, String authToken) {
		JSONObject data = new JSONObject();
		try {
			data.put("auth", authToken);
		} catch(Exception ex) {
			
		}
		
		c.send("pusher:subscribe", data);
	}

	private void sendUnsubscribeMessage(Channel c) {
		JSONObject data = new JSONObject();
		c.send("pusher:unsubscribe", data);
	}

	public void send(String event_name, JSONObject data) {
		JSONObject message = new JSONObject();

		try {
			message.put("event", event_name);
			message.put("data", data);
			webSocket.send(message.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void connect() {
		String path = "/app/" + apiKey + "?client=js&version=" + VERSION;

		try {
			URI url = new URI(PREFIX + HOST + ":" + WS_PORT + path);
			webSocket = new WebSocketConnection(url);
			webSocket.setEventHandler(new WebSocketEventHandler() {
				@Override
				public void onOpen() { 
					// Pusher's onOpen is invoked after we've received a 
					// socket_id in onMessage()
				}

				@Override
				public void onMessage(WebSocketMessage message) {
					try {
						JSONObject jsonMessage = new JSONObject(message.getText());
						String event = jsonMessage.optString("event", null);
						
						if(event.equals("pusher:connection_established" ))
						{
							JSONObject data = new JSONObject(jsonMessage.getString("data"));
							pusherEventListener.onConnect(data.getString("socket_id"));
						} else {
							pusherEventListener.onMessage(jsonMessage.toString());
							dispatchChannelEvent(jsonMessage, event);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				@Override
				public void onClose() {
					pusherEventListener.onDisconnect();
				}
			});

			// Reconnect thread
			pusherThread = new Thread(new Runnable() {
				@Override
				public void run() {
					boolean interrupted = false;
					while (!interrupted) {
						try {
							Thread.sleep(PUSHER_SLEEP_TIME_MS);
							if (!webSocket.isConnected())
								webSocket.connect();
						} catch (InterruptedException e) {
							interrupted = true;
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			});

			pusherThread.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void dispatchChannelEvent(JSONObject jsonMessage, String event) {
		String channelName = jsonMessage.optString("channel", null);
		
		Channel channel = channels.get(channelName);
		if(channel != null) {
			ChannelListener channelListener = channel.channelEvents.get(event);
			
			if(channelListener != null)
				channelListener.onMessage(jsonMessage.toString());
		}
	}

	public class Channel {
		private String channelName;
		private final HashMap<String, ChannelListener> channelEvents;

		public Channel(String _name) {
			channelName = _name;
			channelEvents = new HashMap<String, ChannelListener>();
		}

		public void send(String eventName, JSONObject data) {
			JSONObject message = new JSONObject();

			try {
				data.put("channel", channelName);
				message.put("event", eventName);
				message.put("data", data);
				webSocket.send(message.toString());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		public void bind(String eventName, ChannelListener channelListener) {
			channelEvents.put(eventName, channelListener);
		}

		@Override
		public String toString() {
			return channelName;
		}
	}
}
