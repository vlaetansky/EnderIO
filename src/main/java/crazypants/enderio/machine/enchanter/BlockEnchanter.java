package crazypants.enderio.machine.enchanter;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import cpw.mods.fml.common.network.IGuiHandler;
import crazypants.enderio.BlockEio;
import crazypants.enderio.EnderIO;
import crazypants.enderio.GuiHandler;
import crazypants.enderio.ModObject;
import crazypants.enderio.api.tool.ITool;
import crazypants.enderio.gui.IResourceTooltipProvider;
import crazypants.enderio.machine.AbstractMachineEntity;
import crazypants.enderio.tool.ToolUtil;
import crazypants.util.Util;

public class BlockEnchanter extends BlockEio implements IGuiHandler, IResourceTooltipProvider {

  public static BlockEnchanter create() {
    BlockEnchanter res = new BlockEnchanter();
    res.init();
    return res;
  }

  public static int renderId = -1;

  protected BlockEnchanter() {
    super(ModObject.blockEnchanter.unlocalisedName, TileEnchanter.class);
    setBlockTextureName("enderio:blockEnchanter");
    setLightOpacity(4);
  }

  @Override
  protected void init() {
    super.init();
    EnderIO.guiHandler.registerGuiHandler(GuiHandler.GUI_ID_ENCHANTER, this);
  }

  @Override
  public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase player, ItemStack stack) {
    super.onBlockPlacedBy(world, x, y, z, player, stack);
    int heading = MathHelper.floor_double(player.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
    TileEnchanter te = (TileEnchanter) world.getTileEntity(x, y, z);
    switch (heading) {
    case 0:
      te.setFacing((short) 2);
      break;
    case 1:
      te.setFacing((short) 5);
      break;
    case 2:
      te.setFacing((short) 3);
      break;
    case 3:
      te.setFacing((short) 4);
      break;
    default:
      break;
    }
    if(world.isRemote) {
      return;
    }
    world.markBlockForUpdate(x, y, z);
  }

  @Override
  public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer entityPlayer, int side, float par7, float par8, float par9) {

    ITool tool = ToolUtil.getEquippedTool(entityPlayer);
    if(tool != null) {
      if(entityPlayer.isSneaking() && tool.canUse(entityPlayer.getCurrentEquippedItem(), entityPlayer, x, y, z)) {
        removedByPlayer(world, entityPlayer, x, y, z, false);
        tool.used(entityPlayer.getCurrentEquippedItem(), entityPlayer, x, y, z);
        return true;
      }
    }
    if(entityPlayer.isSneaking()) {
      return false;
    }
    if(!world.isRemote) {
      entityPlayer.openGui(EnderIO.instance, GuiHandler.GUI_ID_ENCHANTER, world, x, y, z);
    }
    return true;
  }

  @Override
  public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean harvested) {
    if(!world.isRemote) {
      TileEntity te = world.getTileEntity(x, y, z);
      if(te instanceof TileEnchanter && !player.capabilities.isCreativeMode) {
        dropAsItem(world, x, y, z, (TileEnchanter) te);
        world.removeTileEntity(x, y, z);
      }
    }
    return super.removedByPlayer(world, player, x, y, z, harvested);
  }

  @Override
  public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
    TileEntity te = world.getTileEntity(x, y, z);
    if(te instanceof TileEnchanter) {
      dropAsItem(world, x, y, z, (TileEnchanter) te);
    }
    super.breakBlock(world, x, y, z, block, meta);
    world.removeTileEntity(x, y, z);
  }

  private void dropAsItem(World world, int x, int y, int z, TileEnchanter te) {
    ItemStack itemStack = new ItemStack(this);
    dropBlockAsItem(world, x, y, z, itemStack);

    if(te.getStackInSlot(0) != null) {
      Util.dropItems(world, te.getStackInSlot(0), x, y, z, true);
    }
    if(te.getStackInSlot(1) != null) {
      Util.dropItems(world, te.getStackInSlot(1), x, y, z, true);
    }
  }

  @Override
  public int quantityDropped(Random p_149745_1_) {
    return 0;
  }

  @Override
  public int getRenderType() {
    return renderId;
  }

  @Override
  public boolean isOpaqueCube() {
    return false;
  }

  @Override
  public boolean renderAsNormalBlock() {
    return false;
  }

  @Override
  public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
    TileEntity te = world.getTileEntity(x, y, z);
    if(te instanceof TileEnchanter) {
      return new ContainerEnchanter(player, player.inventory, (TileEnchanter) te);
    }
    return null;
  }

  @Override
  public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
    TileEntity te = world.getTileEntity(x, y, z);
    if(te instanceof TileEnchanter) {
      return new GuiEnchanter(player, player.inventory, (TileEnchanter) te);
    }
    return null;
  }
  
  @Override
  public String getUnlocalizedNameForTooltip(ItemStack itemStack) {
    return getUnlocalizedName();
  }

}
