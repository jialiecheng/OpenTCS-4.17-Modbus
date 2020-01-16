/*
 * openTCS copyright information:
 * Copyright (c) 2013 Fraunhofer IML
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.guing.components.dialogs;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Collection;
import static java.util.Objects.requireNonNull;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.swing.JPanel;
import org.opentcs.guing.event.OperationModeChangeEvent;
import org.opentcs.guing.event.SystemModelTransitionEvent;
import org.opentcs.guing.model.elements.VehicleModel;
import org.opentcs.guing.persistence.ModelManager;
import org.opentcs.util.event.EventHandler;

/**
 * Shows every vehicle available in the system in a panel.
 *
 * @author Philipp Seifert (Fraunhofer IML)
 */
public class VehiclesPanel
    extends JPanel
    implements EventHandler {

  /**
   * Provides the current system model.
   */
  private final ModelManager modelManager;
  /**
   * A factory for vehicle views.
   */
  private final SingleVehicleViewFactory vehicleViewFactory;
  /**
   * The vehicle views sorted.
   */
  private final SortedSet<SingleVehicleView> vehicleViews = new TreeSet<>();

  /**
   * Creates a new instance.
   *
   * @param modelManager Provides the current system model.
   * @param vehicleViewFactory A factory for vehicle views.
   */
  @Inject
  VehiclesPanel(ModelManager modelManager,
                SingleVehicleViewFactory vehicleViewFactory) {
    this.modelManager = requireNonNull(modelManager, "modelManager");
    this.vehicleViewFactory = requireNonNull(vehicleViewFactory,
                                             "vehicleViewFactory");

    initComponents();
    setPreferredSize(new Dimension(0, 97));
    setMinimumSize(new Dimension(140, 120));
    panelVehicles.setLayout(new ModifiedFlowLayout(FlowLayout.LEFT, 10, 10));
  }

  @Override
  public void onEvent(Object event) {
    if (event instanceof OperationModeChangeEvent) {
      handleModeChange((OperationModeChangeEvent) event);
    }
    if (event instanceof SystemModelTransitionEvent) {
      handleSystemModelTransition((SystemModelTransitionEvent) event);
    }
  }

  private void handleModeChange(OperationModeChangeEvent evt) {
    switch (evt.getNewMode()) {
      case OPERATING:
        setVehicleModels(modelManager.getModel().getVehicleModels());
        break;
      case MODELLING:
      default:
        clearVehicles();
    }
  }

  /**
   * Initializes this panel with the current vehicles.
   *
   * @param vehicleModels The vehicle models.
   */
  public void setVehicleModels(Collection<VehicleModel> vehicleModels) {
    // Remove vehicles of the previous model from panel
    for (SingleVehicleView vehicleView : vehicleViews) {
      panelVehicles.remove(vehicleView);
    }

    // Remove vehicles of the previous model from list
    vehicleViews.clear();
    // Add vehicles of actual model to list
    for (VehicleModel vehicle : vehicleModels) {
      vehicleViews.add(vehicleViewFactory.createSingleVehicleView(vehicle));
    }

    // Add vehicles of actual model to panel, sorted by name
    for (SingleVehicleView vehicleView : vehicleViews) {
      panelVehicles.add(vehicleView);
    }

    panelVehicles.revalidate();
  }

  /**
   * Clears the vehicles in this panel.
   */
  public void clearVehicles() {
    for (SingleVehicleView vehicleView : vehicleViews) {
      panelVehicles.remove(vehicleView);
    }
    vehicleViews.clear();
    repaint();
  }

  @Override
  public void repaint() {
    super.repaint();

    if (vehicleViews != null) {
      for (SingleVehicleView view : vehicleViews) {
        view.repaint();
      }
    }
  }

  private void handleSystemModelTransition(SystemModelTransitionEvent evt) {
    switch (evt.getStage()) {
      case UNLOADING:
        clearVehicles();
        break;
      case LOADED:
        setVehicleModels(modelManager.getModel().getVehicleModels());
        break;
      default:
      // Do nada.
    }
  }

  // CHECKSTYLE:OFF
  /**
   * This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    scrollPaneVehicles = new javax.swing.JScrollPane();
    panelVehicles = new javax.swing.JPanel();

    setName("VehiclesPanel"); // NOI18N
    setLayout(new java.awt.GridLayout(1, 0));

    scrollPaneVehicles.setViewportView(panelVehicles);

    add(scrollPaneVehicles);

    java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n/org/opentcs/plantoverview/panels/vehicleView"); // NOI18N
    getAccessibleContext().setAccessibleName(bundle.getString("vehiclesPanel.title")); // NOI18N
    getAccessibleContext().setAccessibleDescription("");
  }// </editor-fold>//GEN-END:initComponents

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel panelVehicles;
  private javax.swing.JScrollPane scrollPaneVehicles;
  // End of variables declaration//GEN-END:variables
  // CHECKSTYLE:ON
}
