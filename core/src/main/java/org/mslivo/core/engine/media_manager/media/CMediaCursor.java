package org.mslivo.core.engine.media_manager.media;

import java.io.Serializable;

public class CMediaCursor extends CMediaGFX implements Serializable {

    public CMediaCursor(String filename) {
        super(filename);
    }

    public int hotspot_x;

    public int hotspot_y;

}
