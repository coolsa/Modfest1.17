package xyz.coolsa.sound_track.client.networking;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import xyz.coolsa.sound_track.SoundTrack;
import xyz.coolsa.sound_track.SoundTrackConstants;
import xyz.coolsa.sound_track.entity.JukeboxMinecartEntity;
import xyz.coolsa.sound_track.entity.NoteBlockMinecartEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.block.enums.Instrument;

public class ClientPacketHandler {
	public Map<Entity, SoundInstance> playingSongs;

	public ClientPacketHandler() {
		playingSongs = new HashMap<Entity, SoundInstance>();
		ClientPlayNetworking.registerGlobalReceiver(SoundTrackConstants.JUKEBOX_ENTITY_PLAY,
				(client, handler, buf, responseSender) -> {
					this.jukeboxEntityPlayUpdate(client, handler, buf, responseSender);
				});
		ClientPlayNetworking.registerGlobalReceiver(SoundTrackConstants.NOTE_BLOCK_ENTITY_PLAY,
				(client, handler, buf, responseSender) -> {
					this.noteblockEntityPlayUpdate(client, handler, buf, responseSender);
				});
	}

	/**
	 * Play a music disc in a Jukebox Minecart. Stops it if its already playing.
	 * 
	 * @param client
	 * @param handler
	 * @param buf
	 * @param responseSender
	 */
	@SuppressWarnings({ "unlikely-arg-type", "resource" })
	private void jukeboxEntityPlayUpdate(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf,
			PacketSender responseSender) {
		int entityId = buf.readInt(); // read the entity id
		ItemStack record = buf.readItemStack(); // read the item stack from the packet.
		@SuppressWarnings("unused")
		long playSeeking = buf.readLong(); // TODO: Add seeking ability, so jukebox minecarts can start playback at any
											// time. nstb_vorbis_seek?
		client.execute(() -> {
			// verify the entity id we were given is a jukebox minecart entity.
			if (!(client.world.getEntityById(entityId) instanceof JukeboxMinecartEntity)) {
				return; // otherwise exit.
			}
			// if it is an jukeboxminecart entity, lets go ahead and get it.
			JukeboxMinecartEntity entity = (JukeboxMinecartEntity) client.world.getEntityById(entityId);
			// get the currently playing songs on the client.
			SoundInstance instance = this.playingSongs.get(entity);
			// as well as the client's sound system.
			SoundManager soundSystem = MinecraftClient.getInstance().getSoundManager();
			if (soundSystem == null)
				return; // return if there is no sound system
			Text subtitle = null; // otherwise, lets go and try to play the sound and
			// if there is currently a sound playing
			if (instance != null && soundSystem.isPlaying(instance)) {
				soundSystem.stop(instance); // lets stop that sound
				this.playingSongs.remove(instance); // remove it from this class's currently playing songs
				instance = null; // and make sure its seen as null.
				// otherwise
			} else if (record.getItem() instanceof MusicDiscItem) {
				// if the record is a vanilla (or extension of) music disc,
				MusicDiscItem recordItem = (MusicDiscItem) record.getItem(); // get the record item
				instance = new EntityTrackingSoundInstance(recordItem.getSound(), SoundCategory.RECORDS, 4.0f, 1.0f,
						entity, client.world.getRandom().nextLong()); // create a new sound instance tracking the
																		// jukebox minecart
				subtitle = recordItem.getDescription(); // and read the description of the music disc.
				// otherwise
			}
//			else if (SoundTrack.phonos.isLoaded() && SoundTrack.phonos.isCustomMusicDisc(record.getItem())) {
//				// if the record is a CustomMusicDiscItem from Phonos, read the sound event.
//				SoundEvent event = Registries.SOUND_EVENT
//						.get(Identifier.tryParse(record.getOrCreateSubNbt("MusicData").getString("SoundId")));
//				if (event != null) // if the event exists, create an instance.
//					instance = new EntityTrackingSoundInstance(event, SoundCategory.RECORDS, 4.0f, 1.0f, entity,
//							client.world.getRandom().nextLong());
//				try { // also lets try to read the subtitle from the item
//					subtitle = instance.getSoundSet(MinecraftClient.getInstance().getSoundManager()).getSubtitle();
//					if (subtitle == null)
//						throw new Exception(); // fail if subtitle is null
//				} catch (Exception e) { // if it fails, lets just make it a "custom music disc"
//					subtitle = Text.translatable("item.phonos.custom_music_disc");
//				}
//			}
			if (subtitle != null) // if there exists a subtitle, lets display the "now playing" subtitle.
				MinecraftClient.getInstance().inGameHud
						.setOverlayMessage(Text.translatable("record.nowPlaying", subtitle), true);
			// lets put the sound into the currently playing map,
			this.playingSongs.put(entity, instance);
			// and if that sound is not null, lets attempt to play it via the sound system.
			if (instance != null)
				soundSystem.play(instance);
			// no seaking of audio needed, because OOF is it hard for ogg files in java.
		});
	}

	/**
	 * Plays a noteblock from a Noteblock Minecart.
	 * 
	 * @param client
	 * @param handler
	 * @param buf
	 * @param responseSender
	 */
	private void noteblockEntityPlayUpdate(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf,
			PacketSender responseSender) {
		int entityId = buf.readInt(); // read the entity id
		BlockPos pos = buf.readBlockPos(); // read the position of the block below the entity.
		Integer note = buf.readInt(); // and read the tuning of the noteblock minecart.
		client.execute(() -> {
			if (!(client.world.getEntityById(entityId) instanceof NoteBlockMinecartEntity)) {
				return; // if the entity is not a minecart entity, lets skip.
			}
			NoteBlockMinecartEntity entity = (NoteBlockMinecartEntity) client.world.getEntityById(entityId); // otherwise,
			Instrument instrument = Instrument.fromBelowState(entity.world.getBlockState(pos)); // read below.
			SoundEvent sound = instrument.getSound().value();
			// New 1.20 feature support.
			if (entity.world.getEnabledFeatures().contains(FeatureFlags.UPDATE_1_20)) {
				Optional<Instrument> above = Instrument.fromAboveState(entity.world.getBlockState(pos.up().up())); // above
				if (above.isPresent()) {
					instrument = above.get();
					sound = instrument.getSound().value();
					if (instrument.hasCustomSound()) {
				        BlockEntity blockEntity = entity.world.getBlockEntity(pos.up());
				        if (blockEntity instanceof SkullBlockEntity) {
				            Identifier customSound = ((SkullBlockEntity)blockEntity).getNoteBlockSound();
				            if(customSound != null) {
				            	sound = RegistryEntry.of(SoundEvent.of(customSound)).value();
				            }
				        }
					}
				}
			}
			SoundManager soundSystem = MinecraftClient.getInstance().getSoundManager(); // get the sound manager
			// track the entity, play the instrument.
			float notes = (float) Math.pow(2.0, (double) (note - 12) / 12.0);
			if (instrument.shouldSpawnNoteParticles()) {
				entity.world.addParticle(ParticleTypes.NOTE, (double) entity.getX(), (double) entity.getY() + 1.2,
						(double) entity.getZ(), (double) note / 24.0, 0.0, 0.0); // and also particles!
			} else {
				notes = 1.0f;
			}
			EntityTrackingSoundInstance noteInstance = new EntityTrackingSoundInstance(sound, SoundCategory.RECORDS,
					3.0f, notes, entity, client.world.random.nextLong());
			// music is wierd, note frequencies increment in an exponential way, but there
			// are 12 notes, so each increase is really the above.
			soundSystem.play(noteInstance); // now lets play the note.
		});
	}

}
