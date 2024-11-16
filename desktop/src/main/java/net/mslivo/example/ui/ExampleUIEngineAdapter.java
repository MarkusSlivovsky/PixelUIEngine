package net.mslivo.example.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL32;
import com.badlogic.gdx.graphics.OrthographicCamera;
import net.mslivo.core.engine.media_manager.CMediaSprite;
import net.mslivo.core.engine.media_manager.MediaManager;
import net.mslivo.core.engine.ui_engine.*;
import net.mslivo.core.engine.ui_engine.constants.KeyCode;
import net.mslivo.core.engine.ui_engine.media.UIEngineBaseMedia_8x8;
import net.mslivo.core.engine.ui_engine.rendering.PrimitiveRenderer;
import net.mslivo.core.engine.ui_engine.rendering.SpriteRenderer;
import net.mslivo.core.engine.ui_engine.ui.actions.ButtonAction;
import net.mslivo.core.engine.ui_engine.ui.actions.HotKeyAction;
import net.mslivo.core.engine.ui_engine.constants.BUTTON_MODE;
import net.mslivo.core.engine.ui_engine.ui.components.button.TextButton;
import net.mslivo.core.engine.ui_engine.ui.components.viewport.AppViewport;
import net.mslivo.example.ui.media.ExampleBaseMedia;
import net.mslivo.example.ui.windows.ExampleWindowGeneratorP;

public class ExampleUIEngineAdapter implements UIEngineAdapter {
    private static final boolean IM_PERFORMANCE_TEST = false;
    private API api;
    private MediaManager mediaManager;
    private float animation_timer;
    private boolean resetPressed;
    private SpriteRenderer batch;
    private PrimitiveRenderer primitiveRenderer;

    public ExampleUIEngineAdapter() {
    }

    public boolean isResetPressed() {
        return resetPressed;
    }

    public void setResetPressed(boolean resetPressed) {
        this.resetPressed = resetPressed;
    }

    @Override
    public void init(API api, MediaManager mediaManager) {
        this.api = api;
        this.mediaManager = mediaManager;
        this.animation_timer = 0;
        this.batch = new SpriteRenderer(mediaManager);
        this.primitiveRenderer = new PrimitiveRenderer();

        api.config.window.setDefaultEnforceScreenBounds(false);

        // Example Wnd Button
        TextButton createExampleWindowButton = api.component.button.textButton.create(0, 0, 10, 2, "Example Wnd", new ButtonAction() {
            @Override
            public void onRelease() {
                api.addWindow(api.window.createFromGenerator(new ExampleWindowGeneratorP(), "Example Window", mediaManager));
            }

        },  BUTTON_MODE.DEFAULT);

        api.component.button.centerContent(createExampleWindowButton);
        api.addScreenComponent(createExampleWindowButton);

        createExampleWindowButton.buttonAction.onPress();
        createExampleWindowButton.buttonAction.onRelease();

        // Transition Btn
        TextButton transitionBtn = api.component.button.textButton.create(0, 2, 10, 2, "Reset", new ButtonAction() {
            @Override
            public void onRelease() {
                resetPressed = true;
            }
        });
        api.component.button.centerContent(transitionBtn);
        api.addScreenComponent(transitionBtn);

        // HotKey
        api.addHotKey(api.hotkey.create(new int[]{com.badlogic.gdx.Input.Keys.ESCAPE}, new HotKeyAction() {
            @Override
            public void onPress() {
                api.closeAllWindows();
            }
        }));
        api.setMouseTool(api.mouseTool.create("Pointer", null, UIEngineBaseMedia_8x8.UI_CURSOR_ARROW));

        api.config.ui.setFont(UIEngineBaseMedia_8x8.UI_FONT);

        api.config.input.setHardwareMouseEnabled(true);

        api.config.input.setKeyboardMouseEnabled(true);
        api.config.input.setKeyboardMouseButtonsUp(new int[]{KeyCode.Key.UP});
        api.config.input.setKeyboardMouseButtonsDown(new int[]{KeyCode.Key.DOWN});
        api.config.input.setKeyboardMouseButtonsLeft(new int[]{KeyCode.Key.LEFT});
        api.config.input.setKeyboardMouseButtonsRight(new int[]{KeyCode.Key.RIGHT});
        api.config.input.setKeyboardMouseButtonsMouse1(new int[]{KeyCode.Key.CONTROL_LEFT});

        api.config.input.setGamePadMouseEnabled(true);
        api.config.input.setGamePadMouseStickLeftEnabled(true);
        api.config.input.setGamePadMouseStickRightEnabled(true);
        api.config.input.setGamePadMouseButtonsMouse1(new int[]{KeyCode.GamePad.A});
        api.config.input.setGamePadMouseButtonsMouse2(new int[]{KeyCode.GamePad.B});


        api.addHotKey(api.hotkey.create(new int[]{KeyCode.Key.F5}, new HotKeyAction() {
            @Override
            public void onRelease() {
                api.addWindowAsModal(api.composites.modal.createMessageModal("test",new String[]{"test"}, null));
            }
        }));
    }

    @Override
    public void update() {
        APIInput.APIKeyboard keyBoard = api.input.keyboard;
        while (keyBoard.event.keyDownHasNext()) {
            int key = keyBoard.event.keyDownNext();
            switch (key) {
                case KeyCode.Key.Q -> api.input.mouse.emulated.setPosition(10, 10);
                case KeyCode.Key.W -> api.input.mouse.emulated.setPositionPreviousComponent();
                case KeyCode.Key.E -> api.input.mouse.emulated.setPositionNextComponent();
            }
        }
    }


    @Override
    public void render(OrthographicCamera camera, AppViewport appViewPort) {
        animation_timer += Gdx.graphics.getDeltaTime();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Draw app based on data
        batch.setProjectionMatrix(camera.combined);

        batch.begin();

        for (int x = 0; x < api.resolutionWidth(); x += 16) {
            for (int y = 0; y < api.resolutionHeight(); y += 16) {
                batch.drawCMediaAnimation(ExampleBaseMedia.BACKGROUND,
                        x, y, animation_timer);
            }
        }

        batch.end();


        primitiveRenderer.setColorReset();
        if (IM_PERFORMANCE_TEST) {
            primitiveRenderer.setProjectionMatrix(camera.combined);
            long time = System.currentTimeMillis();
            final int VERTEXES = 10000;
            primitiveRenderer.begin();
            for (int i = 0; i < VERTEXES; i++) {
                for (int ix = 0; ix < 10; ix++) {
                    for (int iy = 0; iy < 10; iy++) {
                        primitiveRenderer.setVertexColor(ix / 10f, iy / 10f, 1f, 0.5f);
                        primitiveRenderer.vertex(100 + ix, 100 + iy);
                    }
                }
            }
            primitiveRenderer.end();
            System.out.println(VERTEXES+" Vertexes: "+(System.currentTimeMillis()-time)+"ms");
        }


        primitiveRenderer.setProjectionMatrix(camera.combined);


        // Primitive Drawing Test

        primitiveRenderer.begin(GL20.GL_POINTS);
        primitiveRenderer.setVertexColor(Color.BLUE);
        primitiveRenderer.vertex(50,50);
        primitiveRenderer.vertex(51,50);
        primitiveRenderer.vertex(52,50);
        primitiveRenderer.vertex(53,50);
        primitiveRenderer.vertex(54,50);
        primitiveRenderer.end();

        primitiveRenderer.begin(GL20.GL_LINES);
        primitiveRenderer.setVertexColor(Color.RED);
        primitiveRenderer.vertex(50, 60);
        primitiveRenderer.vertex(55,60);
        primitiveRenderer.end();

        primitiveRenderer.begin(GL20.GL_TRIANGLES);
        primitiveRenderer.setVertexColor(Color.GREEN);
        primitiveRenderer.vertex(50, 70);
        primitiveRenderer.vertex(55,80);
        primitiveRenderer.vertex(60,70);

        primitiveRenderer.vertex(50+15, 70);
        primitiveRenderer.vertex(55+15,80);
        primitiveRenderer.vertex(60+15,70);
        primitiveRenderer.end();

        primitiveRenderer.begin(GL20.GL_TRIANGLE_STRIP);

        primitiveRenderer.setVertexColor(Color.BLUE);
        primitiveRenderer.vertex(100,100);
        primitiveRenderer.vertex(120,100);
        primitiveRenderer.vertex(110,120);

        primitiveRenderer.vertex(100,140);
        primitiveRenderer.vertex(120,140);
        primitiveRenderer.vertex(110,160);
        primitiveRenderer.end();

    }

    @Override
    public void shutdown() {

    }


}
