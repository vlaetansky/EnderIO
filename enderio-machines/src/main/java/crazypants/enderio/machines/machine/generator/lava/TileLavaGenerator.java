package crazypants.enderio.machines.machine.generator.lava;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.enderio.core.api.common.util.ITankAccess;
import com.enderio.core.common.fluid.SmartTank;
import com.enderio.core.common.fluid.SmartTankFluidHandler;
import com.enderio.core.common.util.NNList;

import crazypants.enderio.base.fluid.SmartTankFluidMachineHandler;
import crazypants.enderio.base.machine.base.te.AbstractCapabilityGeneratorEntity;
import crazypants.enderio.base.paint.IPaintable;
import crazypants.enderio.base.power.PowerDistributor;
import crazypants.enderio.machines.config.config.LavaGenConfig;
import crazypants.enderio.machines.init.MachineObject;
import info.loenwind.autosave.annotations.Storable;
import info.loenwind.autosave.annotations.Store;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import static crazypants.enderio.machines.capacitor.CapacitorKey.LAVAGEN_POWER_BUFFER;
import static crazypants.enderio.machines.capacitor.CapacitorKey.LAVAGEN_POWER_GEN;
import static crazypants.enderio.machines.capacitor.CapacitorKey.LAVAGEN_POWER_LOSS;

@Storable
public class TileLavaGenerator extends AbstractCapabilityGeneratorEntity implements IPaintable.IPaintableTileEntity, ITankAccess.IExtendedTankAccess {

  @Store
  public int burnTime = 0;
  @Store
  public int heat = 0;
  @Store
  protected final @Nonnull SmartTank tank = new SmartTank(FluidRegistry.LAVA, LavaGenConfig.tankSize.get());

  private PowerDistributor powerDis;
  private int coolingSide = 0;

  public TileLavaGenerator() {
    super(LAVAGEN_POWER_BUFFER, LAVAGEN_POWER_GEN);
    getEnergy().setEnergyLoss(LAVAGEN_POWER_LOSS);
    tank.setTileEntity(this);
  }

  @Override
  public @Nonnull String getMachineName() {
    return MachineObject.block_lava_generator.getUnlocalisedName();
  }

  @Override
  public boolean isActive() {
    return burnTime > 0;
  }

  @Override
  public void onNeighborBlockChange(@Nonnull IBlockState state, @Nonnull World worldIn, @Nonnull BlockPos posIn, @Nonnull Block blockIn,
      @Nonnull BlockPos fromPos) {
    super.onNeighborBlockChange(state, worldIn, posIn, blockIn, fromPos);
    if (powerDis != null) {
      powerDis.neighboursChanged();
    }
  }

  @Override
  protected boolean processTasks(boolean redstoneCheck) {
    if (heat > 0) {
      heat = Math.max(0, heat - LavaGenConfig.heatLossPassive.get());
      if (heat > 0 && LavaGenConfig.heatLossActive.get() > 0) {
        coolingSide++;
        if (coolingSide > 5) {
          coolingSide = 0;
        }
        final EnumFacing side = NNList.FACING.get(coolingSide);
        BlockPos pos2 = pos.offset(side);
        if (world.isBlockLoaded(pos2)) {
          Block block = world.getBlockState(pos2).getBlock();
          if (block instanceof IFluidBlock || block instanceof BlockLiquid) {
            IFluidHandler targetFluidHandler = FluidUtil.getFluidHandler(world, pos, side.getOpposite());
            if (targetFluidHandler != null) {
              FluidStack fluidStack = targetFluidHandler.drain(1000, false);
              if (fluidStack != null && fluidStack.amount >= 1000 && fluidStack.getFluid().getTemperature(fluidStack) < getHeatDisplayValue()) {
                heat = Math.max(0, heat - LavaGenConfig.heatLossActive.get());
                if (fluidStack.getFluid() == FluidRegistry.WATER && random.nextFloat() < LavaGenConfig.activeCoolingEvaporatesWater.get()) {
                  world.setBlockToAir(pos2);
                }
              }
            }
          } else if (block == Blocks.ICE || block == Blocks.FROSTED_ICE || block == Blocks.PACKED_ICE) {
            heat = Math.max(0, heat - LavaGenConfig.heatLossActive.get());
            if (random.nextFloat() < LavaGenConfig.activeCoolingLiquefiesIce.get()) {
              if (world.provider.doesWaterVaporize()) {
                world.setBlockToAir(pos2);
              } else {
                world.setBlockState(pos2, Blocks.WATER.getDefaultState());
              }
            }
          }
        }
      }
    }

    if (redstoneCheck && !getEnergy().isFull()) {
      if (burnTime > 0) {
        getEnergy().setEnergyStored(getEnergy().getEnergyStored() + getPowerGenPerTick());
        burnTime--;
        heat = Math.min(heat + LavaGenConfig.heatGain.get(), getMaxHeat());
      }
      if (burnTime <= 0 && !tank.isEmpty()) {
        tank.drain(1, true);
        burnTime = getLavaBurntime() / Fluid.BUCKET_VOLUME;
      }
    }

    if (getHeat() > LavaGenConfig.overheatThreshold.get() && shouldDoWorkThisTick(20)) {
      BlockPos pos2 = pos.up((int) (random.nextGaussian() * 3f)).east((int) (random.nextGaussian() * 5f)).north((int) (random.nextGaussian() * 5f));
      if (world.isAirBlock(pos2)) {
        world.setBlockState(pos2, Blocks.FIRE.getDefaultState());
      }
    }

    transmitEnergy();

    return false;
  }

  private int getLavaBurntime() {
    return LavaGenConfig.useVanillaBurnTime.get() ? 20000 : TileEntityFurnace.getItemBurnTime(new ItemStack(Items.LAVA_BUCKET));
  }

  protected int getPowerGenPerTick() {
    return (int) Math.max(1, getEnergy().getMaxUsage() * getHeatFactor());
  }

  protected float getHeatFactor() {
    return Math.max(LavaGenConfig.minEfficiency.get(), 1f - getHeat());
  }

  protected float getHeat() {
    return heat / (float) getMaxHeat();
  }

  protected float getHeatDisplayValue() {
    float factor = getHeatFactor();
    int ambient = FluidRegistry.WATER.getTemperature();
    int reallyhot = FluidRegistry.LAVA.getTemperature();
    return ambient + (reallyhot - ambient) * factor;
  }

  protected int getMaxHeat() {
    return getLavaBurntime() * LavaGenConfig.maxHeatFactor.get();
  }

  private boolean transmitEnergy() {
    if (powerDis == null) {
      powerDis = new PowerDistributor(getPos());
    }
    int canTransmit = Math.min(getEnergy().getEnergyStored(), getEnergy().getMaxUsage() * 2);
    if (canTransmit <= 0) {
      return false;
    }
    int transmitted = powerDis.transmitEnergy(world, canTransmit);
    getEnergy().setEnergyStored(getEnergy().getEnergyStored() - transmitted);
    return transmitted > 0;
  }

  private SmartTankFluidHandler smartTankFluidHandler;

  protected SmartTankFluidHandler getSmartTankFluidHandler() {
    if (smartTankFluidHandler == null) {
      smartTankFluidHandler = new SmartTankFluidMachineHandler(this, tank);
    }
    return smartTankFluidHandler;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facingIn) {
    if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
      return (T) getSmartTankFluidHandler().get(facingIn);
    }
    return super.getCapability(capability, facingIn);
  }

  @Override
  @Nullable
  public FluidTank getInputTank(FluidStack forFluidType) {
    if (tank.canFill(forFluidType)) {
      return tank;
    }
    return null;
  }

  @Override
  @Nonnull
  public FluidTank[] getOutputTanks() {
    return new FluidTank[0];
  }

  @Override
  public void setTanksDirty() {
    markDirty();
  }

  @Override
  @Nonnull
  public List<ITankData> getTankDisplayData() {
    return Collections.<ITankData> singletonList(new ITankData() {

      @Override
      @Nonnull
      public EnumTankType getTankType() {
        return EnumTankType.INPUT;
      }

      @Override
      @Nullable
      public FluidStack getContent() {
        return tank.getFluid();
      }

      @Override
      public int getCapacity() {
        return tank.getCapacity();
      }
    });
  }

}
