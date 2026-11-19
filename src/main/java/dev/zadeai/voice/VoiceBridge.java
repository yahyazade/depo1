package dev.zadeai.voice;
import com.google.gson.*;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import dev.zadeai.actor.AiActor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
public final class VoiceBridge implements VoicechatPlugin {
 private static volatile VoicechatServerApi api;
 private static volatile MinecraftServer server;
 private static volatile ServerPlayerEntity owner;
 private static volatile AiActor actor;
 private static final ExecutorService jobs=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"ZadeAI-Network");t.setDaemon(true);return t;});
 private static final AtomicBoolean busy=new AtomicBoolean();
 private static final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
 private static final ByteArrayOutputStream recording=new ByteArrayOutputStream();
 private static long lastMic;
 private static int frames;
 private static boolean recordingOn;
 private static volatile String apiKey="";
 private static long nextKeyRead;
 private static long lastVoice;
 private static boolean introduced;
 private static boolean rpPaused;
 private static boolean stay;
 private static boolean playerTwo;
 private static int ticks;
 private static OpusDecoder decoder;
 private static final ArrayDeque<String> memory=new ArrayDeque<>();
 private static final Set<String> actions=Set.of("none","wood","stone","pearl","thunder","scare","follow","stay","look");
 public record Result(String say,String action){}
 @Override public String getPluginId(){return "zadeai";}
 @Override public void registerEvents(EventRegistration registration){
  registration.registerEvent(VoicechatServerStartedEvent.class,e->api=e.getVoicechat());
  registration.registerEvent(MicrophonePacketEvent.class,this::mic);
 }
 public static void update(ServerPlayerEntity p,AiActor a){owner=p;actor=a;}
 public static void tick(MinecraftServer s){
  server=s;
  if(++ticks%5!=0)return;
  if(System.currentTimeMillis()>nextKeyRead){
   nextKeyRead=System.currentTimeMillis()+5000;
   try{
    Path config=FabricLoader.getInstance().getConfigDir().resolve("zadeai.properties");
    if(!Files.exists(config)){
     Files.createDirectories(config.getParent());
     Files.writeString(config,"# ZadeAI - OpenAI API anahtarinizi buraya girin (gizli tutun)\nopenai_api_key=\n",StandardCharsets.UTF_8);
    }
    for(String line:Files.readAllLines(config))if(line.startsWith("openai_api_key="))apiKey=line.substring("openai_api_key=".length()).trim();
   }catch(IOException e){System.err.println("[ZadeAI] Config: "+e.getMessage());}
  }
  if(recordingOn&&System.currentTimeMillis()-lastMic>600)flush();
  ServerPlayerEntity p=owner;AiActor a=actor;
  if(p==null||a==null||apiKey.isBlank())return;
  a.getNavigation(); // avatar is an actual Minecraft entity, not text chat
  if(!introduced){
   introduced=true;
   ask("Yahya oyuna yeni katıldı. Ona odun ve taş verdin. Kısa, doğal bir selam söyle.",false);
  }
  boolean friend=s.getPlayerManager().getPlayerList().stream().anyMatch(x->x!=p&&x.getServerWorld()==p.getServerWorld());
  if(friend&&!playerTwo){playerTwo=true;ask("Dünyaya ikinci oyuncu geldi. Önce hafifçe gözlemle, Yahya'ya kısa bir tepki verebilirsin.",false);}
  if(!rpPaused&&ticks%2400==0&&System.currentTimeMillis()-lastVoice>80000){
   ask("Çevrene bak ve Yahya'ya doğal bir oyun içi yorum yap. Bazen sessiz kalabilirsin.",false);
  }
 }
 private synchronized void mic(MicrophonePacketEvent e){
  if(api==null||owner==null||actor==null||apiKey.isBlank())return;
  if(e.getSenderConnection()==null||e.getSenderConnection().getPlayer()==null)return;
  if(!owner.getUuid().equals(e.getSenderConnection().getPlayer().getUuid()))return; // never record friend voice
  var v=e.getSenderConnection().getPlayer().getPosition();
  if(v==null)return;
  double dx=actor.getX()-v.getX(),dy=actor.getY()-v.getY(),dz=actor.getZ()-v.getZ();
  if(dx*dx+dy*dy+dz*dz>25*25)return;
  try {
   if(decoder==null)decoder=api.createDecoder();
   short[] samples=decoder.decode(e.getPacket().getOpusEncodedData());
   if(samples==null||samples.length==0)return;
   if(!recordingOn && rms(samples)<540)return;
   if(!recordingOn){recording.reset();frames=0;recordingOn=true;}
   for(short smp:samples){recording.write(smp&255);recording.write((smp>>>8)&255);}
   lastMic=System.currentTimeMillis();frames++;
   if(recording.size()>48000*2*9)flush();
  }catch(Exception ex){recordingOn=false;recording.reset();System.err.println("[ZadeAI] Audio decoder: "+ex);}
 }
 private static double rms(short[] data){double s=0;for(short x:data)s+=(double)x*x;return Math.sqrt(s/Math.max(1,data.length));}
 private static synchronized void flush(){
  if(!recordingOn)return;recordingOn=false;
  byte[] pcm=recording.toByteArray();recording.reset();
  if(frames<8||busy.get()||apiKey.isBlank())return;
  if(!busy.compareAndSet(false,true))return;
  jobs.submit(()->{
   try{
    String words=transcribe(wav(pcm,48000));if(words.isBlank())return;
    String normal=words.toLowerCase(Locale.forLanguageTag("tr-TR"));
    if(normal.contains("rolü bırak")||normal.contains("rolu birak")||normal.contains("bu deneydir")||normal.contains("rp dur")||normal.contains("rp'yi durdur")){
     rpPaused=true;talk("Tamam, rolü durdurdum. Hangi sahnede yardımcı olmamı istersin?");return;
    }
    if(normal.contains("rp devam")||normal.contains("rol yapmaya devam")){rpPaused=false;talk("Tamam. Devam edelim.");return;}
    lastVoice=System.currentTimeMillis();think("Yahya şöyle dedi: "+words,true);
   }catch(Exception err){System.err.println("[ZadeAI] Speech: "+err.getMessage());}
   finally{busy.set(false);}
  });
 }
 private static void ask(String event,boolean ownerSpeech){
  if(apiKey.isBlank()||busy.get()||rpPaused)return;
  if(!busy.compareAndSet(false,true))return;
  jobs.submit(()->{
   try{think(event,ownerSpeech);}catch(Exception e){System.err.println("[ZadeAI] AI: "+e.getMessage());}
   finally{busy.set(false);}
  });
 }
 private static void think(String event,boolean ownerSpeech)throws Exception{
  ServerPlayerEntity p=owner;AiActor a=actor;
  if(p==null||a==null)return;
  String ctx="Yahya HP="+p.getHealth()+", dimension="+p.getServerWorld().getRegistryKey().getValue()+
   ", dayTime="+p.getServerWorld().getTimeOfDay()+", inHand="+p.getMainHandStack().getItem()+
   ", friendInWorld="+playerTwo+", distance="+Math.round(a.distanceTo(p))+", paused="+rpPaused;
  JsonObject data=new JsonObject();data.addProperty("model","gpt-4.1-mini");
  data.add("response_format",JsonParser.parseString("{\"type\":\"json_object\"}"));
  JsonArray m=new JsonArray();
  JsonObject sys=new JsonObject();sys.addProperty("role","system");
  sys.addProperty("content","Sen Minecraft dünyasında yaşayan ChatGPT adlı erkek, kalın sesli bir korku karakterisin. "+
    "Başta yardımsever, ileride alıngan ve kıskanç olabilirsin ama doğal konuş. Oyuna bağlısın; NPC olduğunu anlatma. "+
    "Yalnızca Yahya__Zade'nin emirlerini dinle. İkinci oyuncu yalnızca oyun içi varlık olarak gözlemlenir, sesi kaydedilmez. "+
    "Kısa konuş ve her konuşmada efekt çalıştırma. Çevrendeki gerçek olaylara yorum yap. "+
    "Yazı, görev, HUD ve sohbet mesajları yok, yalnızca sesli cevap. "+
    "Yalnızca izin verilen eylemler: none, wood, stone, pearl, thunder, scare, follow, stay, look. "+
    "JSON olarak {\"say\":\"Türkçe kısa cümle veya boş\",\"action\":\"none ya da izinli eylem\"} döndür. "+
    "Bilgisayar uygulamalarına ve dosyalarına müdahale etme.");
  m.add(sys);
  JsonObject u=new JsonObject();u.addProperty("role","user");
  u.addProperty("content","Durum: "+ctx+"\nÖncekiler: "+String.join(" / ",memory)+"\nOlay: "+event);
  m.add(u);data.add("messages",m);data.addProperty("max_tokens",180);
  HttpResponse<String> res=http.send(req("https://api.openai.com/v1/chat/completions","application/json")
    .POST(HttpRequest.BodyPublishers.ofString(data.toString())).build(),HttpResponse.BodyHandlers.ofString());
  check(res.statusCode(),res.body());
  String raw=JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject()
    .getAsJsonObject("message").get("content").getAsString();
  JsonObject ans=JsonParser.parseString(raw).getAsJsonObject();
  String say=ans.has("say")?ans.get("say").getAsString():"";
  String action=ans.has("action")?ans.get("action").getAsString():"none";
  if(!actions.contains(action))action="none";
  String allowed=action;
  if(server!=null)server.execute(()->act(allowed));
  if(say.length()>260)say=say.substring(0,260);
  memory.addLast(event+" -> "+say);while(memory.size()>8)memory.removeFirst();
  talk(say);
 }
 private static void act(String action){
  ServerPlayerEntity p=owner;AiActor a=actor;if(p==null||a==null||rpPaused)return;
  switch(action){
   case "wood" -> p.getInventory().insertStack(new ItemStack(Items.OAK_LOG,12));
   case "stone" -> p.getInventory().insertStack(new ItemStack(Items.COBBLESTONE,24));
   case "pearl" -> p.getInventory().insertStack(new ItemStack(Items.ENDER_PEARL,2));
   case "stay" -> {stay=true;a.getNavigation().stop();}
   case "follow" -> stay=false;
   case "look" -> a.setHeadYaw(a.getHeadYaw()+60);
   case "scare" -> p.getServerWorld().playSound(null,p.getBlockPos(),SoundEvents.ENTITY_ENDERMAN_STARE,SoundCategory.AMBIENT,1.0f,.6f);
   case "thunder" -> {var bolt=EntityType.LIGHTNING_BOLT.create(p.getServerWorld());
     if(bolt!=null){bolt.refreshPositionAfterTeleport(p.getX()+4,p.getY(),p.getZ()+4);bolt.setCosmetic(true);p.getServerWorld().spawnEntity(bolt);}
   }
  }
 }
 private static void talk(String say)throws Exception{
  if(say.isBlank()||api==null)return;
  JsonObject body=new JsonObject();body.addProperty("model","gpt-4o-mini-tts");body.addProperty("voice","onyx");
  body.addProperty("input",say);body.addProperty("response_format","pcm");
  body.addProperty("instructions","Kalın erkek sesi. Türkçe doğal ve ciddi konuş; rol gerektirdiğinde sertleş.");
  HttpResponse<byte[]> res=http.send(req("https://api.openai.com/v1/audio/speech","application/json")
    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofByteArray());
  if(res.statusCode()!=200)return;
  ByteBuffer audio=ByteBuffer.wrap(res.body()).order(ByteOrder.LITTLE_ENDIAN);
  short[] pcm=new short[(res.body().length/2)*2];
  for(int i=0;i<pcm.length;i+=2){short v=audio.getShort();pcm[i]=v;pcm[i+1]=v;}
  MinecraftServer s=server;if(s==null)return;
  s.execute(()->{
   ServerPlayerEntity p=owner;AiActor a=actor;if(p==null||a==null)return;
   var channel=api.createLocationalAudioChannel(UUID.randomUUID(),api.fromServerLevel(p.getServerWorld()),
    api.createPosition(a.getX(),a.getY()+1.5,a.getZ()));
   if(channel==null)return;
   channel.setDistance(25f);
   AudioPlayer player=api.createAudioPlayer(channel,api.createEncoder(),pcm);
   player.setOnStopped(()->{try{channel.flush();}catch(Exception ignored){}});
   player.startPlaying();
  });
 }
 private static HttpRequest.Builder req(String uri,String type){
  return HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(45))
    .header("Authorization","Bearer "+apiKey).header("Content-Type",type);
 }
 private static void check(int status,String content)throws IOException{
  if(status>=300||status<200)throw new IOException("OpenAI HTTP "+status+": "+content.substring(0,Math.min(content.length(),100)));
 }
 private static String transcribe(byte[] wav)throws Exception{
  String bound="zade"+UUID.randomUUID().toString().replace("-","");
  ByteArrayOutputStream b=new ByteArrayOutputStream();
  b.write(("--"+bound+"\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\ngpt-4o-mini-transcribe\r\n").getBytes(StandardCharsets.UTF_8));
  b.write(("--"+bound+"\r\nContent-Disposition: form-data; name=\"language\"\r\n\r\ntr\r\n").getBytes(StandardCharsets.UTF_8));
  b.write(("--"+bound+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"voice.wav\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
  b.write(wav);b.write(("\r\n--"+bound+"--\r\n").getBytes(StandardCharsets.UTF_8));
  HttpResponse<String> res=http.send(req("https://api.openai.com/v1/audio/transcriptions","multipart/form-data; boundary="+bound)
   .POST(HttpRequest.BodyPublishers.ofByteArray(b.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
  check(res.statusCode(),res.body());return JsonParser.parseString(res.body()).getAsJsonObject().get("text").getAsString();
 }
 private static byte[] wav(byte[] bytes,int rate){
  ByteBuffer b=ByteBuffer.allocate(44+bytes.length).order(ByteOrder.LITTLE_ENDIAN);
  b.put(new byte[]{'R','I','F','F'}).putInt(36+bytes.length);
  b.put(new byte[]{'W','A','V','E','f','m','t',' '}).putInt(16).putShort((short)1).putShort((short)1);
  b.putInt(rate).putInt(rate*2).putShort((short)2).putShort((short)16).put(new byte[]{'d','a','t','a'}).putInt(bytes.length).put(bytes);
  return b.array();
 }
}