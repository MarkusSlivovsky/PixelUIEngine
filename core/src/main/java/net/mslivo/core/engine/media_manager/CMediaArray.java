package net.mslivo.core.engine.media_manager;

import com.badlogic.gdx.math.Interpolation;

import java.io.Serializable;
import java.util.Objects;

public final class CMediaArray extends CMediaSprite implements Serializable {
    public int regionWidth;
    public int regionHeight;
    public int frameOffset;
    public int frameLength;

    public CMediaArray(){
    }

    public CMediaArray(String filename, int regionWidth, int regionHeight) {
        this(filename, regionWidth, regionHeight, 0, Integer.MAX_VALUE);
    }

    public CMediaArray(String filename, int regionWidth, int regionHeight, int frameOffset, int frameLength) {
        super(filename);
        this.regionWidth = regionWidth;
        this.regionHeight = regionHeight;
        this.frameOffset = frameOffset;
        this.frameLength = frameLength;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        CMediaArray that = (CMediaArray) o;
        return regionWidth == that.regionWidth && regionHeight == that.regionHeight && frameOffset == that.frameOffset && frameLength == that.frameLength;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + regionWidth;
        result = 31 * result + regionHeight;
        result = 31 * result + frameOffset;
        result = 31 * result + frameLength;
        return result;
    }
}
