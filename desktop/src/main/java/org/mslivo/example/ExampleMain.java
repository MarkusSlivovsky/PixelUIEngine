package org.mslivo.example;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import org.mslivo.core.engine.game_engine.GameEngine;
import org.mslivo.core.engine.media_manager.MediaManager;
import org.mslivo.core.engine.tools.Tools;
import org.mslivo.core.engine.ui_engine.UIAdapter;
import org.mslivo.core.engine.ui_engine.UIEngine;
import org.mslivo.example.data.ExampleData;
import org.mslivo.example.engine.ExampleEngineAdapter;
import org.mslivo.example.ui.ExampleUIAdapter;
import org.mslivo.example.ui.media.ExampleBaseMedia;

import java.nio.file.Path;

public class ExampleMain extends ApplicationAdapter {

    /* Subsystems */

    private MediaManager mediaManager;
    private ExampleData data;
    public ExampleEngineAdapter engineAdapter;
    private GameEngine<ExampleEngineAdapter, ExampleData> gameEngine;
    public UIAdapter UIAdapter;
    private UIEngine<UIAdapter> uiEngine;

    /* --- */

    private long profile_time_gui, profile_time_engine, profile_time_render;

    private long timer_debug_info;

    public ExampleMain() {

    }

    @Override
    public void resize(int width, int height) {
        if (this.uiEngine != null) this.uiEngine.resize(width, height);
    }

    @Override
    public void create() {

        this.writeAndReadExampleDataFile();

        this.bootEngine();

    }


    private void writeAndReadExampleDataFile() {
        final Path DATA_FILE = Path.of(System.getProperty("user.home") + "/org/mslivo/example/test.data");
        ExampleData exampleData = new ExampleData();
        try {
            Tools.Log.inProgress("Writing DataFile");
            Tools.Log.done();
            Tools.File.writeObjectToFile(exampleData, DATA_FILE);
            Tools.Log.inProgress("Loading DataFile");
            this.data = (ExampleData) Tools.File.readObjectFromFile(DATA_FILE);
            Tools.Log.done();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void bootEngine() {
        Tools.Log.inProgress("Loading Assets");
        this.mediaManager = new MediaManager();

        this.mediaManager.prepareGUICMedia();
        this.mediaManager.prepareCMedia(ExampleBaseMedia.ALL);
        this.mediaManager.loadAssets();

        Tools.Log.done();
        // Engine
        Tools.Log.inProgress("Starting Engine Subsystem");
        this.engineAdapter = new ExampleEngineAdapter();
        this.gameEngine = new GameEngine<>(this.engineAdapter, this.data);

        Tools.Log.done();
        // Input/Render
        Tools.Log.inProgress("Starting UI Subsystem");
        this.UIAdapter = new ExampleUIAdapter(this.gameEngine);
        this.uiEngine = new UIEngine<>(
                this.UIAdapter,
                this.mediaManager,
                ExampleMainConstants.internalResolutionWidth, ExampleMainConstants.internalResolutionHeight,
                ExampleMainConstants.viewportMode, true);
        Tools.Log.done();
    }


    @Override
    public void render() {

        // Update UI Engine / Gather Input / Process Output
        profile_time_gui = System.currentTimeMillis();
        this.uiEngine.update();
        profile_time_gui = System.currentTimeMillis() - profile_time_gui;

        // Update Game Engine / Process Input / Create Output
        profile_time_engine = System.currentTimeMillis();
        this.gameEngine.update();
        profile_time_engine = System.currentTimeMillis() - profile_time_engine;

        // Render Everything
        profile_time_render = System.currentTimeMillis();
        this.uiEngine.render();
        profile_time_render = System.currentTimeMillis() - profile_time_render;


        // Debug Out
        if (System.currentTimeMillis() - timer_debug_info > 5000) {
            Tools.Log.benchmark("UI: " + profile_time_gui + "ms","Render: " + profile_time_render + "ms");
            timer_debug_info = System.currentTimeMillis();
        }
    }

    @Override
    public void dispose() {
        Tools.Log.benchmark("Shutting down...");
        this.shutdownEngine();
        Tools.Log.done();
        Gdx.app.exit();
        System.exit(0);
    }

    private void shutdownEngine() {
        this.uiEngine.shutdown();
        this.gameEngine.shutdown();
        this.mediaManager.shutdown();
    }


}