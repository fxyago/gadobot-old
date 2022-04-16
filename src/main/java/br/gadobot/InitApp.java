package br.gadobot;

import java.io.BufferedReader;
import java.io.FileReader;

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

public class InitApp {
	
	public static final long SELF_ID = 906888499075112971l;
	public static final String PREFIX = ";";
	public static final Logger LOGGER = LoggerFactory.getLogger(InitApp.class);
	public static String discordToken;
	public static JDA jda;
	public static final String FILE_SEPARATOR = System.getProperty("file.separator");
	
	@SuppressWarnings("unused")
	public static void main(String[] args) throws LoginException, InterruptedException {
		
		LOGGER.info("Initializing...");
		SpotifyHandler spotifyInitializer = new SpotifyHandler();
		
		try (BufferedReader br = new BufferedReader(new FileReader(System.getProperty("user.dir") + FILE_SEPARATOR + "discord_token.txt"))) {
			
			discordToken = br.readLine();
			jda = JDABuilder.create(discordToken,
					GatewayIntent.GUILD_MESSAGES,
					GatewayIntent.GUILD_VOICE_STATES)
					.addEventListeners(new CommandListener())
					.disableCache(CacheFlag.ACTIVITY, CacheFlag.EMOTE, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS)
					.setActivity(Activity.listening(PREFIX + "help"))
					.build();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
}