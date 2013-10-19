package net.bicou.redmine.data.json;

import java.util.Calendar;
import java.util.List;

public class Journal {
	public long id;
	public User user;
	public String notes;
	public Calendar created_on;
	public List<JournalDetail> details;

	// These are not from the JSON/Redmine server
	public List<String> formatted_details;
	public String formatted_notes;
}
