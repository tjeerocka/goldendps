package com.goldendps.model;

public enum MeterMode
{
	DAMAGE("Damage"),
	DPS("DPS"),
	HEALING("Healing"),
	OVERHEALING("Overhealing"),
	DAMAGE_TAKEN("Damage taken");

	private final String label;

	MeterMode(String label)
	{
		this.label = label;
	}

	public String getLabel()
	{
		return label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
