package org.mslivo.core.engine.ui_engine.gui.actions;

import org.mslivo.core.engine.media_manager.color.FColor;
import org.mslivo.core.engine.media_manager.media.CMediaGFX;
import org.mslivo.core.engine.ui_engine.gui.components.inventory.Inventory;
import org.mslivo.core.engine.ui_engine.gui.components.list.List;
import org.mslivo.core.engine.ui_engine.gui.tooltip.ToolTip;

import java.util.HashSet;

public abstract class ListAction<T extends Object> extends CommonActions {

    public CMediaGFX icon(T listItem) {
        return null;
    }

    public ToolTip toolTip(T listItem) {
        return null;
    }

    public int iconArrayIndex(T listItem) { // if CMedia is CMediaArray
        return 0;
    }

    public String text(T listItem) {
        return listItem.toString();
    }

    public void onItemSelected(T listItem) {
    }

    public void onItemsSelected(HashSet<T> listItems) {
    }

    public void onScrolled(float scrolled) {

    }
    /* Drag */

    public void onDragFromList(List fromList, int fromIndex, int toIndex) {
    }

    public void onDragFromInventory(Inventory fromInventory, int from_x, int from_y, int toIndex) {
    }

    public boolean canDragFromList(List list) {
        return false;
    }

    public boolean canDragFromInventory(Inventory fromInventory) {
        return false;
    }

    public FColor cellColor(T listItem) {
        return null;
    }

    public void onDragIntoScreen(T listItem, int index, int mouseX, int mouseY) {
    }

    public boolean canDragIntoScreen() {return false;}

}
