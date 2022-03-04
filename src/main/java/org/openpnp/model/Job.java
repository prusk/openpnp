/*
 * Copyright (C) 2011 Jason von Nieda <jason@vonnieda.org>
 * 
 * This file is part of OpenPnP.
 * 
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * For more information about OpenPnP visit http://openpnp.org
 */

package org.openpnp.model;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openpnp.util.IdentifiableList;
import org.openpnp.util.ResourceUtils;
import org.openpnp.util.Utils2D;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Commit;
import org.simpleframework.xml.core.Persist;

/**
 * A Job specifies a list of one or more PanelLocations and/or BoardLocations.
 */
@Root(name = "openpnp-job")
public class Job extends AbstractModelObject implements PropertyChangeListener {
    @ElementList(required = false)
    protected List<PanelLocation> panelLocations = new ArrayList<>();

    @Deprecated
    @ElementList(required = false)
    protected List<Panel> panels = new ArrayList<>();

    @ElementList
    private ArrayList<BoardLocation> boardLocations = new ArrayList<>();

    private transient File file;
    private transient boolean dirty;
    private transient PanelLocation rootPanelLocation;
    
    public Job() {
        rootPanelLocation = new PanelLocation(new Panel("root"));
        rootPanelLocation.setLocalToParentTransform(new AffineTransform());
        
        addPropertyChangeListener(this);
    }

    @SuppressWarnings("unused")
    @Commit
    private void commit() {
        for (BoardLocation boardLocation : boardLocations) {
            boardLocation.addPropertyChangeListener(this);
        }
        for (PanelLocation panelLocation : panelLocations) {
            panelLocation.addPropertyChangeListener(this);
        }
        
        //Convert deprecated list of Panels to list of PanelLocations
        if (panels != null && !panels.isEmpty()) {
            Panel panel = panels.get(0);
            PanelLocation panelLocation = new PanelLocation(panel);
            panelLocation.setParent(null);
            panelLocation.setLocation(boardLocations.get(0).getLocation());
            panelLocation.addPropertyChangeListener(this);
            panelLocations.add(panelLocation);
            panels = null;
        }
    }
    
    public List<BoardLocation> getBoardLocations() {
        return Collections.unmodifiableList(boardLocations);
    }

    public void addBoardLocation(BoardLocation boardLocation) {
        Object oldValue = boardLocations;
        boardLocations = new ArrayList<>(boardLocations);
        boardLocations.add(boardLocation);
        firePropertyChange("boardLocations", oldValue, boardLocations);
        boardLocation.addPropertyChangeListener(this);
    }

    public void removeBoardLocation(BoardLocation boardLocation) {
        Object oldValue = boardLocations;
        boardLocations = new ArrayList<>(boardLocations);
        boardLocations.remove(boardLocation);
        firePropertyChange("boardLocations", oldValue, boardLocations);
        boardLocation.removePropertyChangeListener(this);
    }

    public void removeAllBoards() {
        ArrayList<BoardLocation> oldValue = boardLocations;
        boardLocations = new ArrayList<>();

        firePropertyChange("boardLocations", (Object) oldValue, boardLocations);

        for (int i = 0; i < oldValue.size(); i++) {
            oldValue.get(i).removePropertyChangeListener(this);
        }
    }

    public void addPanel(Panel panel) {
        panels.add(panel);
    }

    public void removeAllPanels() {
        panels.clear();
    }

    public List<Panel> getPanels() {
        return panels;
    }

    // In the first release of the Auto Panelize software, there is assumed to be a
    // single panel in use, even though the underlying plumbing supports a list of
    // panels. This function is intended to let the rest of OpenPNP know if the
    // autopanelize function is being used
    public boolean isUsingPanel() {
        if (panelLocations == null) {
            return false;
        }

        if (panelLocations.size() >= 1) {
            return true;
        }

        return false;
    }

    public List<PanelLocation> getPanelLocations() {
        return Collections.unmodifiableList(panelLocations);
    }

    public void addPanelLocation(PanelLocation panelLocation) {
        Object oldValue = panelLocations;
        panelLocations = new ArrayList<>(panelLocations);
        panelLocations.add(panelLocation);
        firePropertyChange("panelLocations", oldValue, panelLocations);
        panelLocation.addPropertyChangeListener(this);
    }

    public void removePanelLocation(PanelLocation panelLocation) {
        Object oldValue = panelLocations;
        panelLocations = new ArrayList<>(panelLocations);
        panelLocations.remove(panelLocation);
        firePropertyChange("boardLocations", oldValue, boardLocations);
        panelLocation.removePropertyChangeListener(this);
    }

    public void removeAllPanelLocations() {
        List<PanelLocation> oldValue = panelLocations;
        panelLocations = new ArrayList<>();

        firePropertyChange("panelLocations", (Object) oldValue, panelLocations);

        for (int i = 0; i < oldValue.size(); i++) {
            oldValue.get(i).removePropertyChangeListener(this);
        }
    }

    private void panelLocationToList(PanelLocation panelLocation, List<FiducialLocatableLocation> list) {
        list.add(panelLocation);
        for (FiducialLocatableLocation child : panelLocation.getPanel().getChildren()) {
            if (child instanceof PanelLocation) {
                panelLocationToList((PanelLocation) child, list);
            }
            else if (child instanceof BoardLocation) {
                list.add((BoardLocation) child);
            }
            else {
                throw new UnsupportedOperationException("Instance type " + child.getClass() + " not supported.");
            }
        }
    }
    
    public List<FiducialLocatableLocation> getFiducialLocatableLocations() {
        List<FiducialLocatableLocation> retList = new ArrayList<>();
        panelLocationToList(rootPanelLocation, retList);
        return Collections.unmodifiableList(retList);
    }
    
    public void addFiducialLocatableLocation(FiducialLocatableLocation fiducialLocatableLocation) {
        if (fiducialLocatableLocation instanceof PanelLocation) {
            addPanelLocation((PanelLocation) fiducialLocatableLocation);
        }
        else if (fiducialLocatableLocation instanceof BoardLocation) {
            addBoardLocation((BoardLocation) fiducialLocatableLocation);
        }
        else {
            throw new UnsupportedOperationException("Instance type " + fiducialLocatableLocation.getClass() + " not supported.");
        }
    }

    public void removeFiducialLocatableLocation(FiducialLocatableLocation fiducialLocatableLocation) {
        if (fiducialLocatableLocation instanceof PanelLocation) {
            removePanelLocation((PanelLocation) fiducialLocatableLocation);
        }
        else if (fiducialLocatableLocation instanceof BoardLocation) {
            removeBoardLocation((BoardLocation) fiducialLocatableLocation);
        }
        else {
            throw new UnsupportedOperationException("Instance type " + fiducialLocatableLocation.getClass() + " not supported.");
        }
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        Object oldValue = this.file;
        this.file = file;
        firePropertyChange("file", oldValue, file);
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        boolean oldValue = this.dirty;
        this.dirty = dirty;
        firePropertyChange("dirty", oldValue, dirty);
    }

    public PanelLocation getRootPanelLocation() {
        return rootPanelLocation;
    }

    public void setRootPanelLocation(PanelLocation rootPanel) {
        PanelLocation oldValue = this.rootPanelLocation;
        this.rootPanelLocation = rootPanel;
        firePropertyChange("rootPanel", oldValue, rootPanel);
    }

    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getSource() != Job.this || !evt.getPropertyName().equals("dirty")) {
            setDirty(true);
        }
    }
}
