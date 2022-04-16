package br.gadobot;

import javax.security.auth.login.LoginException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.gadobot.handlers.SpotifyHandler;
import br.gadobot.listeners.CommandListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

public class Gado {
	
	public static final long SELF_ID = 906888499075112971l;
	public static final String PREFIX = "[";
	public static final Logger LOGGER = LoggerFactory.getLogger(Gado.class);
	public static JDA jda;
	public static SpotifyHandler spotifyHandler;
	
	public static void main(String[] args) throws LoginException, InterruptedException {
		
		LOGGER.info("Initializing...");
		spotifyHandler = new SpotifyHandler();
		
		jda = JDABuilder.create("OTA2ODg4NDk5MDc1MTEyOTcx.YYfLuw.claq2OZkGDur3UMhjz19ma-UIck",
				GatewayIntent.GUILD_MESSAGES,
				GatewayIntent.GUILD_VOICE_STATES)
				.addEventListeners(new CommandListener())
				.disableCache(CacheFlag.ACTIVITY, CacheFlag.EMOTE, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS)
				.setActivity(Activity.listening(PREFIX + "help"))
				.build();
		
	}
	
}