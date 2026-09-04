package org.gielinor.profilesync;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RunelitePlan
{
	private final String planKey;
	private final String theme;
	private final String targetLabel;
	private final String targetDetail;
	private final String freshnessLabel;
	private final String revision;
	private final Instant updatedAt;
	private final Instant expiresAt;
	private final List<Item> items;

	RunelitePlan(
		String planKey,
		String theme,
		String targetLabel,
		String targetDetail,
		String freshnessLabel,
		String revision,
		Instant updatedAt,
		Instant expiresAt,
		List<Item> items)
	{
		this.planKey = planKey;
		this.theme = theme;
		this.targetLabel = targetLabel;
		this.targetDetail = targetDetail;
		this.freshnessLabel = freshnessLabel;
		this.revision = revision;
		this.updatedAt = updatedAt;
		this.expiresAt = expiresAt;
		this.items = Collections.unmodifiableList(new ArrayList<>(items));
	}

	String getPlanKey()
	{
		return planKey;
	}

	String getTheme()
	{
		return theme;
	}

	String getTargetLabel()
	{
		return targetLabel;
	}

	String getTargetDetail()
	{
		return targetDetail;
	}

	String getFreshnessLabel()
	{
		return freshnessLabel;
	}

	String getRevision()
	{
		return revision;
	}

	Instant getUpdatedAt()
	{
		return updatedAt;
	}

	Instant getExpiresAt()
	{
		return expiresAt;
	}

	List<Item> getItems()
	{
		return items;
	}

	static final class Item
	{
		private final String id;
		private final String label;
		private final int required;
		private final Integer owned;
		private final Integer missing;
		private final String state;
		private final String note;
		private final boolean optional;
		private final String kind;

		Item(String id, String label, int required, Integer owned, Integer missing, String state, String note, boolean optional, String kind)
		{
			this.id = id;
			this.label = label;
			this.required = required;
			this.owned = owned;
			this.missing = missing;
			this.state = state;
			this.note = note;
			this.optional = optional;
			this.kind = kind;
		}

		String getId()
		{
			return id;
		}

		String getLabel()
		{
			return label;
		}

		int getRequired()
		{
			return required;
		}

		Integer getOwned()
		{
			return owned;
		}

		Integer getMissing()
		{
			return missing;
		}

		String getState()
		{
			return state;
		}

		String getNote()
		{
			return note;
		}

		boolean isOptional()
		{
			return optional;
		}

		String getKind()
		{
			return kind;
		}

		boolean isReady()
		{
			return "ready".equals(state);
		}
	}
}
