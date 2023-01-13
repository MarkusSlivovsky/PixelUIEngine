package org.mslivo.core.engine.ui_engine.gui.components.viewport;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import org.mslivo.core.engine.ui_engine.gui.actions.GameViewPortAction;
import org.mslivo.core.engine.ui_engine.gui.components.Component;

public class GameViewPort extends Component {

    public float camera_x, camera_y, camera_z;

    public float camera_zoom;

    public FrameBuffer frameBuffer;

    public TextureRegion textureRegion;

    public GameViewPortAction gameViewPortAction;

    public long updateTimer;

    public int updateTime;

}
