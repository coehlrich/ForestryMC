/*******************************************************************************
 * Copyright (c) 2011-2014 SirSengir.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v3
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-3.0.txt
 *
 * Various Contributors including, but not limited to:
 * SirSengir (original work), CovertJaguar, Player, Binnie, MysteriousAges
 ******************************************************************************/
package forestry.greenhouse.climate.modifiers;

import java.util.Collection;

import net.minecraft.nbt.NBTTagCompound;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import forestry.api.climate.ClimateStateType;
import forestry.api.climate.ClimateType;
import forestry.api.climate.IClimateState;
import forestry.api.greenhouse.IClimateHousing;
import forestry.core.climate.ClimateState;
import forestry.core.config.Config;
import forestry.core.utils.Translator;
import forestry.greenhouse.api.climate.IClimateContainer;
import forestry.greenhouse.api.climate.IClimateData;
import forestry.greenhouse.api.climate.IClimateModifier;
import forestry.greenhouse.api.climate.IClimateSource;

public class ClimateSourceModifier implements IClimateModifier {

	@Override
	public IClimateState modifyTarget(IClimateContainer container, IClimateState newState, IClimateState oldState, NBTTagCompound data) {
		Collection<IClimateSource> sources = container.getClimateSources();
		if (sources.isEmpty()) {
			data.removeTag("rangeUp");
			data.removeTag("rangeDown");
			data.removeTag("change");
			return newState;
		}
		IClimateHousing housing = container.getParent();
		double sizeModifier = housing.getSize() / Config.climateSourceRange;
		double size = sizeModifier * 0.2D;
		sizeModifier = Math.max(sizeModifier, 1.0D);

		container.recalculateBoundaries(sizeModifier);

		IClimateState boundaryUp = container.getBoundaryUp();
		IClimateState boundaryDown = container.getBoundaryDown();

		data.setTag("rangeUp", boundaryUp.writeToNBT(new NBTTagCompound()));
		data.setTag("rangeDown", boundaryDown.writeToNBT(new NBTTagCompound()));

		IClimateState targetedState = container.getTargetedState();
		if(!targetedState.isPresent()){
			return newState;
		}
		int workedSources = 0;
		IClimateState target = getTargetOrBound(oldState, container.getBoundaryDown(), container.getBoundaryUp(), targetedState);
		IClimateState changeState = new ClimateState(data.getCompoundTag("change"), ClimateStateType.CHANGE);

		for (IClimateSource source : container.getClimateSources()) {
			IClimateState state = source.work(oldState, target);
			if (state.isPresent()) {
				boolean hasChange = false;
				double temperatureChange = state.getTemperature();
				double humidityChange = state.getHumidity();
				if (temperatureChange != 0) {
					temperatureChange /= sizeModifier;
					hasChange = true;
				}
				if (humidityChange != 0) {
					humidityChange /= sizeModifier;
					hasChange = true;
				}
				if (hasChange) {
					changeState = changeState.addTemperature((float) temperatureChange);
					changeState = changeState.addHumidity((float) humidityChange);
					workedSources++;
				}
			}
		}
		if (workedSources == 0) {
			return newState.add(changeState);
		}
		data.setTag("change", changeState.writeToNBT(new NBTTagCompound()));
		return newState.add(changeState);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addData(IClimateContainer container, IClimateState climateState, NBTTagCompound nbtData, IClimateData data) {
		IClimateState rangeDown = new ClimateState(nbtData.getCompoundTag("rangeDown"), ClimateStateType.MUTABLE);
		IClimateState rangeUp = new ClimateState(nbtData.getCompoundTag("rangeUp"), ClimateStateType.MUTABLE);
		IClimateState change = new ClimateState(nbtData.getCompoundTag("change"), ClimateStateType.MUTABLE);

		data.addData(ClimateType.HUMIDITY, Translator.translateToLocal("for.gui.modifier.sources.range.up"), rangeUp.getHumidity())
			.addData(ClimateType.HUMIDITY, Translator.translateToLocal("for.gui.modifier.sources.range.down"), rangeDown.getHumidity())
			.addData(ClimateType.HUMIDITY, Translator.translateToLocal("for.gui.modifier.sources.change"), change.getHumidity());

		data.addData(ClimateType.TEMPERATURE, Translator.translateToLocal("for.gui.modifier.sources.range.up"), rangeUp.getTemperature())
			.addData(ClimateType.TEMPERATURE, Translator.translateToLocal("for.gui.modifier.sources.range.down"), rangeDown.getTemperature())
			.addData(ClimateType.TEMPERATURE, Translator.translateToLocal("for.gui.modifier.sources.change"), change.getTemperature());
	}

	@Override
	public int getPriority() {
		return -1;
	}

	private IClimateState getTargetOrBound(IClimateState climateState, IClimateState boundaryDown, IClimateState boundaryUp, IClimateState targetedState) {
		float temperature = climateState.getTemperature();
		float humidity = climateState.getHumidity();
		float targetTemperature = targetedState.getTemperature();
		float targetHumidity = targetedState.getHumidity();
		float humidityBoundaryUp = boundaryUp.getHumidity();
		float humidityBoundaryDown = boundaryDown.getHumidity();
		float temperatureBoundaryUp = boundaryUp.getTemperature();
		float temperatureBoundaryDown = boundaryDown.getTemperature();
		if (targetTemperature > temperature) {
			temperature = Math.min(targetTemperature, temperatureBoundaryUp);
		} else if (targetTemperature < temperature) {
			temperature = Math.max(targetTemperature, temperatureBoundaryDown);
		}
		if (targetHumidity > humidity) {
			humidity = Math.min(targetHumidity, humidityBoundaryUp);
		} else if (targetHumidity < humidity) {
			humidity = Math.max(targetHumidity, humidityBoundaryDown);
		}
		return new ClimateState(temperature, humidity, ClimateStateType.IMMUTABLE);
	}
}