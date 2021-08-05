package xyz.coolsa.sound_track;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Clearable;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

public class JukeboxMinecartEntity extends AbstractMinecartEntity implements Clearable {

	private ItemStack record = ItemStack.EMPTY;
	private boolean powered = false;
	private BlockPos pos = this.getBlockPos();

	public JukeboxMinecartEntity(EntityType<? extends JukeboxMinecartEntity> type, World world) {
		super(type, world);
	}

	protected JukeboxMinecartEntity(World world, double x, double y, double z) {
		super(SoundTrackConstants.JUKEBOX_MINECART_ENTITY, world, x, y, z);
	}

	@Override
	public Type getMinecartType() {
		// We return the chest type, because thats the weight i think it should have.
		return Type.CHEST;
	}

	@Override
	public ItemStack getPickBlockStack() {
		return new ItemStack(SoundTrackConstants.JUKEBOX_MINECART_ITEM);
	}

	@Override
	public void dropItems(DamageSource damageSource) {
		super.dropItems(damageSource);
		if (this.world.getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) {
			this.dropItem(Blocks.JUKEBOX);
			this.dropStack(this.record);
		}
	}

	@Override
	public BlockState getDefaultContainedBlock() {
		return Blocks.JUKEBOX.getDefaultState();
	}

	@Override
	public int getDefaultBlockOffset() {
		return 8;
	}

	@Override
	public void clear() {
		this.record = ItemStack.EMPTY;
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		if (nbt.contains("RecordItem", NbtElement.COMPOUND_TYPE)) {
			this.record = ItemStack.fromNbt(nbt.getCompound("RecordItem"));
		}
		if (nbt.contains("Powered", NbtElement.INT_TYPE)) {
			this.powered = nbt.getInt("Powered") == 1;
		}
		if (nbt.contains("Position", NbtElement.LONG_TYPE)) {
			this.pos = BlockPos.fromLong(nbt.getLong("BlockPos"));
		}
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		if (!this.record.isEmpty()) {
			nbt.put("RecordItem", this.record.writeNbt(new NbtCompound()));
		}
		nbt.putInt("Powered", (this.powered) ? 1 : 0);
		nbt.putLong("Position", this.pos.asLong());
		return nbt;
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		ActionResult result = ActionResult.SUCCESS;
		if (!player.world.isClient) {
			if (player.getStackInHand(hand).getItem() instanceof MusicDiscItem && this.record.isEmpty()) {
				this.record = player.getStackInHand(hand).copy(); //copy the players record
				this.record.setCount(1); //set the count to 1 (only play 1 record lmao)
				if (!player.isCreative()) { //if they are not in creative
					player.getStackInHand(hand).decrement(1); //decrement the records held by 1, in line with vanilla.
				}
				player.incrementStat(Stats.PLAY_RECORD); //increment their stat.
				this.playRecord(); //actually play the record
				result = ActionResult.CONSUME; //its a consume action.
			} else if (!this.record.isEmpty()) { //if we already have something in the minecart,
				double randomX = this.world.random.nextFloat() * 0.7 - 0.5; //random position near the minecart
				double randomY = this.world.random.nextFloat() * 0.7 + 0.66;
				double randomZ = this.world.random.nextFloat() * 0.7 - 0.5;
				ItemEntity entity = new ItemEntity(this.world, this.getX() + randomX, this.getY() + randomY,
						this.getZ() + randomZ, this.record.copy()); // create the item entity.
				entity.setToDefaultPickupDelay(); // we go ahead and give it a default pickup delay
				this.world.spawnEntity(entity); //spawn the item
				this.record = ItemStack.EMPTY; //the jukebox minecart is now empty
				this.playRecord(); //and update the record playback.
			}
		}
		return result;
	}

	private void playRecord() {
		PacketByteBuf buf = PacketByteBufs.create();
		int entityId = this.getId();
		buf.writeInt(entityId);
		buf.writeItemStack(this.record);
		buf.writeLong(0);
		for (ServerPlayerEntity players : PlayerLookup.around((ServerWorld) world, this.getBlockPos(), 128))
			ServerPlayNetworking.send(players, SoundTrackConstants.JUKEBOX_MINECART_PLAY, buf);
	}

	public ItemStack getRecord() {
		return this.record;
	}

	@Override
	public void onActivatorRail(int x, int y, int z, boolean powered) {
		if (powered && !this.powered) {
			this.playRecord();
		}
		this.powered = powered;
	}

	@Override
	protected void moveOnRail(BlockPos pos, BlockState state) {
		if (!state.isOf(Blocks.ACTIVATOR_RAIL))
			this.powered = false;
		this.pos = pos;
		super.moveOnRail(pos, state);
	}

	@Override
	protected void moveOffRail() {
		this.powered = false;
		super.moveOffRail();
	}

}
