package gadoBot;

//import java.util.List;

import javax.security.auth.login.LoginException;

import gadoBot.Handlers.SpotifyHandler;
import gadoBot.Listeners.CommandListener;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
//import net.dv8tion.jda.api.entities.Member;
//import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

public class Gado {
	
	public static final long selfId = 906888499075112971l;
	public static final String PREFIX = "[";
	
	public static SpotifyHandler spotifyHandler = new SpotifyHandler();
	
	public static void main(String[] args) throws LoginException, InterruptedException {
		
		JDABuilder.create("OTA2ODg4NDk5MDc1MTEyOTcx.YYfLuw.OSyTbAKNYscE4DDiaww8aGuiJmo",
				GatewayIntent.GUILD_MESSAGES,
				GatewayIntent.GUILD_VOICE_STATES)
				.addEventListeners(new CommandListener())
				.disableCache(CacheFlag.ACTIVITY, CacheFlag.EMOTE, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS)
				.setActivity(Activity.listening(PREFIX + "help"))
				.build();
	}

//	public static boolean isBotConnectedToChannel(VoiceChannel channel) {
//		List<Member> members = channel.getMembers();
//		for (Member member : members)
//			if (member.getIdLong() == selfId)
//				return true;
//		return false;
//	}
	
}
