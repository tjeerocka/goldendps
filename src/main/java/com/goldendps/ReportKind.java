package com.goldendps;

/** What a party report shows. */
enum ReportKind
{
	OVERALL("Overall damage"),
	BOSS("Boss damage"),
	HEALING("Healing");

	final String label;

	ReportKind(String label)
	{
		this.label = label;
	}
}
