package edu.nus.cs5248.dashplayer.video;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.util.Log;
import android.util.Xml;

public class Playlist implements Iterable<VideoSegmentInfo> {
	protected static final String TAG = "Playlist";
	
	protected static class QualitySpec implements Comparable<QualitySpec> {
		int verticalResolution;
		int requiredBandwidth;
		
		public QualitySpec(final int verticalQuality, final int requiredBandwidth) {
			this.verticalResolution = verticalQuality;
			this.requiredBandwidth = requiredBandwidth;
		}
		
		@Override
		public int compareTo(QualitySpec rhs) {
			if (this == rhs) return 0;
			return (Integer.valueOf(this.verticalResolution)).compareTo(Integer.valueOf(rhs.verticalResolution));
		}
	}

	public static Playlist createFromMPD(String mpd) {
		Playlist playlist = new Playlist();
		
		if (!playlist.initWithMPD(mpd)) {
			Log.e(TAG, "Failed to parse MPD");
			return null;
		}
		
		return playlist;
	}
	
	public Playlist() {
		this.segmentInfos = new ArrayList<VideoSegmentInfo>();
		this.duration = "PT0S";
		this.minBufferTime = "PT0S";
		this.qualities = new ArrayList<QualitySpec>();
	}
	
	public void addSegmentSource(int index, int quality, String sourceURL) {
		VideoSegmentInfo segment = null;
		
		if (index >= segmentInfos.size()) {
			segment = new VideoSegmentInfo();
			segmentInfos.add(segment);
		} else {
			segment = segmentInfos.get(index);
		}
		
		segment.setURLForQuality(quality, sourceURL);
	}
	
	/**
	 * Provide the recommended video quality for the given bandwidth, 
	 * based on the specification in MPD playlist.
	 * 
	 * @param bandwidth bandwidth in bytes per second
	 * @return
	 */
	public int getQualityForBandwidth(long bandwidth) {
		for (QualitySpec qs : qualities) {
			if ((qs.requiredBandwidth / 8) <= bandwidth) {
				return qs.verticalResolution;
			}
		}
		
		Log.i(TAG, "WARNING: no suitable quality for bandwidth=" + bandwidth);
		return qualities.get(qualities.size() - 1).verticalResolution;
	}
	
	private boolean initWithMPD(String mpd) {

		try {
			XmlPullParser parser = Xml.newPullParser();
			parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
			parser.setInput(new StringReader(mpd));

			int eventType = parser.getEventType();
			
			int quality = 0;
			int segmentIndex = 0;

			while (eventType != XmlPullParser.END_DOCUMENT) {
				switch (eventType) {
				case XmlPullParser.START_DOCUMENT:
					break;

				case XmlPullParser.START_TAG:
					String tagName = parser.getName();
					
					if (tagName.equalsIgnoreCase(Playlist.MPD_TAG)) {
						this.duration = parser.getAttributeValue(null, Playlist.MEDIA_PRESENTATION_DURATION);
						this.minBufferTime = parser.getAttributeValue(null, Playlist.MIN_BUFFER_TIME);
						Log.v(TAG, "[MPD] duration=" + this.duration + " minBufferTime=" + this.minBufferTime);
					}
					else if (tagName.equalsIgnoreCase(Playlist.REPRESENTATION)) {
						String width = parser.getAttributeValue(null, Playlist.WIDTH);
						String height = parser.getAttributeValue(null, Playlist.HEIGHT);
						String bandwidth = parser.getAttributeValue(null, Playlist.BANDWIDTH);
						String minBufferTime = parser.getAttributeValue(null, Playlist.MIN_BUFFER_TIME);
						
						quality = Integer.parseInt(height);
						this.qualities.add(new QualitySpec(quality, Integer.parseInt(bandwidth)));
						
						Log.v(TAG, "[Representation] width=" + width + " height=" + height + " bandwidth=" + bandwidth + " minBufferTime=" + minBufferTime);
					}
					else if (tagName.equalsIgnoreCase(Playlist.SEGMENT_INFO)) {
						String duration = parser.getAttributeValue(null, Playlist.DURATION);
						segmentIndex = 0;
						Log.v(TAG, "[SegmentInfo] duration=" + duration);
					}
					else if (tagName.equalsIgnoreCase(Playlist.URL)) {
						String sourceURL = parser.getAttributeValue(null, Playlist.SOURCE_URL);
						
						this.addSegmentSource(segmentIndex++, quality, sourceURL);
						
						Log.v(TAG, "[URL] sourceURL=" + sourceURL);
					}
					break;
				}
				eventType = parser.next();
			}

		} catch (XmlPullParserException e) {
			Log.e(TAG, "Parse exception: " + e.getMessage());
			return false;
		} catch (IOException e) {
			Log.e(TAG, "IO exception: " + e.getMessage());
			return false;
		} catch (NumberFormatException e) {
			Log.e(TAG, "Malformed response: " + e.getMessage());
			return false;
		}
		
		Collections.sort(qualities, Collections.reverseOrder());

		return true;
	}
	
	private String duration;
	private String minBufferTime;
	private List<VideoSegmentInfo> segmentInfos;
	private ArrayList<QualitySpec> qualities;
	
	static final String MPD_TAG = "MPD";
	static final String MEDIA_PRESENTATION_DURATION = "mediaPresentationDuration";
	static final String MIN_BUFFER_TIME = "minBufferTime";
	static final String REPRESENTATION = "Representation";
	static final String WIDTH = "width";
	static final String HEIGHT = "height";
	static final String BANDWIDTH = "bandwidth";
	static final String SEGMENT_INFO = "SegmentInfo";
	static final String DURATION = "duration";
	static final String URL = "Url";
	static final String SOURCE_URL = "sourceURL";
	
	@Override
	public Iterator<VideoSegmentInfo> iterator() {
		return this.segmentInfos.iterator();
	}
}
