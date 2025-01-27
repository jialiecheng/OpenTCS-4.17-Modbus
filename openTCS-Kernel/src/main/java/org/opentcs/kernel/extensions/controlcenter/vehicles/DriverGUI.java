/**
 * Copyright (c) The openTCS Authors.
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.kernel.extensions.controlcenter.vehicles;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import org.opentcs.components.kernel.*;
import static com.google.common.base.Preconditions.checkState;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import static java.util.Objects.requireNonNull;
import java.util.ResourceBundle;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import org.opentcs.access.Kernel;
import org.opentcs.access.LocalKernel;
import org.opentcs.components.kernel.services.TCSObjectService;
import org.opentcs.data.model.Point;
import org.opentcs.drivers.vehicle.SimVehicleCommAdapter;
import org.opentcs.drivers.vehicle.VehicleCommAdapter;
import org.opentcs.drivers.vehicle.VehicleCommAdapterFactory;
import org.opentcs.kernel.vehicles.VehicleCommAdapterRegistry;
import org.opentcs.util.Comparators;
import org.opentcs.util.gui.BoundsPopupMenuListener;
import org.opentcs.util.gui.StringListCellRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Frame containing all vehicles and detailed information.
 *
 * @author Stefan Walter (Fraunhofer IML)
 * @author Philipp Seifert (Fraunhofer IML)
 */
@SuppressWarnings("deprecation")
public class DriverGUI
    extends org.opentcs.components.kernel.ControlCenterPanel {

  /**
   * This class's Logger.
   */
  private static final Logger LOG = LoggerFactory.getLogger(DriverGUI.class);
  /**
   * This class's resource bundle.
   */
  private final ResourceBundle bundle
      = ResourceBundle.getBundle("org/opentcs/kernel/controlcenter/vehicles/Bundle");
  /**
   * Proxy kernel to communicate with.
   */
  private final LocalKernel kernel;
  /**
   * The object service.
   */
  private final TCSObjectService objectService;
  /**
   * The comm adapter registry.
   */
  private final VehicleCommAdapterRegistry commAdapterRegistry;
  /**
   * Manages attachment of drivers with vehicles.
   */
  private final AttachmentManager attachManager;
  /**
   * The pool of vehicle entries.
   */
  private final VehicleEntryPool vehicleEntryPool;
  /**
   * A flag indicating whether this KernelExtension has been plugged in already.
   */
  private boolean initialized;

  /**
   * Creates a new instance.
   *
   * @param kernel The kernel.
   * @param objectService The object service.
   * @param attachManager The attachment manager.
   * @param commAdapterRegistry The comm adapter registry.
   * @param vehicleEntryPool The pool of vehicle entries.
   */
  @Inject
  public DriverGUI(@Nonnull LocalKernel kernel,
                   @Nonnull TCSObjectService objectService,
                   @Nonnull AttachmentManager attachManager,
                   @Nonnull VehicleCommAdapterRegistry commAdapterRegistry,
                   @Nonnull VehicleEntryPool vehicleEntryPool) {
    this.kernel = requireNonNull(kernel, "kernel");
    this.objectService = requireNonNull(objectService, "objectService");
    this.attachManager = requireNonNull(attachManager, "attachManager");
    this.commAdapterRegistry = requireNonNull(commAdapterRegistry, "commAdapterRegistry");
    this.vehicleEntryPool = requireNonNull(vehicleEntryPool, "vehicleEntryPool");

    initComponents();

    vehicleTable.setDefaultRenderer(VehicleCommAdapterFactory.class,
                                    new VehicleCommAdapterFactoryTableCellRenderer());
    // Initialize detail panels.
    vehicleDetailPanel.add(new DetailPanel());
  }

  @Override
  public boolean isInitialized() {
    return initialized;
  }

  @Override
  public void initialize() {
    if (initialized) {
      LOG.debug("Already initialized.");
      return;
    }

    // Verify that the kernel is in a state in which controlling vehicles is possible.
    Kernel.State kernelState = kernel.getState();
    checkState(Kernel.State.OPERATING.equals(kernelState),
               "Cannot work in kernel state %s",
               kernelState);

    EventQueue.invokeLater(() -> {
      initVehicleList();
    });

    initialized = true;
  }

  @Override
  public void terminate() {
    if (!initialized) {
      LOG.debug("Not initialized.");
      return;
    }

    initialized = false;
  }

  private void initVehicleList() {
    VehicleTableModel model = (VehicleTableModel) vehicleTable.getModel();
    vehicleEntryPool.getEntries().forEach((vehicleName, entry) -> {
      model.addData(entry);
      entry.addPropertyChangeListener(model);
    });

    vehicleTable.getComponentPopupMenu().setEnabled(!model.getVehicleEntries().isEmpty());

    initAdapterComboBoxes();
  }

  private void enableAllCommAdapters() {
    enableCommAdapters(vehicleEntryPool.getEntries().values());
  }

  private void enableSelectedCommAdapters() {
    enableCommAdapters(getSelectedVehicleEntries());
  }

  private void enableCommAdapters(Collection<VehicleEntry> selectedEntries) {
    selectedEntries.stream()
        .map(entry -> entry.getCommAdapter())
        .filter(adapter -> adapter != null)
        .filter(adapter -> !adapter.isEnabled())
        .forEach(adapter -> adapter.enable());
  }

  private void disableAllCommAdapters() {
    disableCommAdapters(vehicleEntryPool.getEntries().values());
  }

  private void disableSelectedCommAdapters() {
    disableCommAdapters(getSelectedVehicleEntries());
  }

  private void disableCommAdapters(Collection<VehicleEntry> selectedEntries) {
    selectedEntries.stream()
        .map(entry -> entry.getCommAdapter())
        .filter(adapter -> adapter != null)
        .filter(adapter -> adapter.isEnabled())
        .forEach(adapter -> adapter.disable());
  }

  /**
   * Initializes the combo boxes with available adapters for every vehicle.
   */
  private void initAdapterComboBoxes() {
    SingleCellEditor adapterCellEditor = new SingleCellEditor(vehicleTable);
    SingleCellEditor pointsCellEditor = new SingleCellEditor(vehicleTable);

    int index = 0;
    for (VehicleEntry entry : vehicleEntryPool.getEntries().values()) {
      initCommAdaptersComboBox(entry, index, adapterCellEditor);
      initPointsComboBox(index, pointsCellEditor);
      index++;
    }

    vehicleTable.getColumn(VehicleTableModel.ADAPTER_COLUMN_IDENTIFIER)
        .setCellEditor(adapterCellEditor);
    vehicleTable.getColumn(VehicleTableModel.POSITION_COLUMN_IDENTIFIER)
        .setCellEditor(pointsCellEditor);
  }

  private void initCommAdaptersComboBox(VehicleEntry vehicleEntry,
                                        int rowIndex,
                                        SingleCellEditor adapterCellEditor) {
    final CommAdapterComboBox comboBox = new CommAdapterComboBox();
    commAdapterRegistry.findFactoriesFor(vehicleEntry.getVehicle())
        .forEach(factory -> comboBox.addItem(factory));
    // Set the selection to the attached comm adapter, (The vehicle is already attached to a comm
    // adapter due to auto attachment on startup.)
    comboBox.setSelectedItem(vehicleEntry.getCommAdapterFactory());
    comboBox.setRenderer(new AdapterFactoryCellRenderer());
    comboBox.addPopupMenuListener(new BoundsPopupMenuListener());
    comboBox.addItemListener((ItemEvent evt) -> {
      if (evt.getStateChange() == ItemEvent.DESELECTED) {
        return;
      }

      // If we selected a comm adapter that's already attached, do nothing.
      if (Objects.equals(evt.getItem(), vehicleEntry.getCommAdapterFactory())) {
        LOG.debug("{} is already attached to: {}", vehicleEntry.getVehicleName(), evt.getItem());
        return;
      }

      int reply = JOptionPane.showConfirmDialog(
          null,
          bundle.getString("CommAdapterComboBox.confirmation.driverChange.text"),
          bundle.getString("CommAdapterComboBox.confirmation.driverChange.title"),
          JOptionPane.YES_NO_OPTION);
      if (reply == JOptionPane.NO_OPTION) {
        return;
      }

      attachManager.attachAdapterToVehicle(getSelectedVehicleName(),
                                           (VehicleCommAdapterFactory) comboBox.getSelectedItem());
    });
    adapterCellEditor.setEditorAt(rowIndex, new DefaultCellEditor(comboBox));

    vehicleEntry.addPropertyChangeListener(comboBox);
  }

  /**
   * If a loopback adapter was chosen, this method initializes the combo boxes
   * with positions the user can set the vehicle to.
   *
   * @param rowIndex An index indicating which row this combo box belongs to
   * @param pointsCellEditor The <code>SingleCellEditor</code> containing
   * the combo boxes.
   */
  private void initPointsComboBox(int rowIndex, SingleCellEditor pointsCellEditor) {
    final JComboBox<Point> pointComboBox = new JComboBox<>();

    objectService.fetchObjects(Point.class).stream()
        .sorted(Comparators.objectsByName())
        .forEach(point -> pointComboBox.addItem(point));
    pointComboBox.setSelectedIndex(-1);
    pointComboBox.setRenderer(new StringListCellRenderer<>(x -> x == null ? "" : x.getName()));

    pointComboBox.addItemListener((ItemEvent e) -> {
      Point newPoint = (Point) e.getItem();
      VehicleEntry vehicleEntry = vehicleEntryPool.getEntryFor(getSelectedVehicleName());
      if (vehicleEntry.getCommAdapter() instanceof SimVehicleCommAdapter) {
        SimVehicleCommAdapter adapter = (SimVehicleCommAdapter) vehicleEntry.getCommAdapter();
        adapter.initVehiclePosition(newPoint.getName());
      }
      else {
        VehicleCommAdapter adapter = (VehicleCommAdapter) vehicleEntry.getCommAdapter();
        adapter.getProcessModel().setVehiclePosition(newPoint.getName());
        LOG.info("Vehicle {}: Not a simulation adapter -> not setting initial position.",
                  vehicleEntry.getVehicle().getName());
      }
    });
    pointsCellEditor.setEditorAt(rowIndex, new DefaultCellEditor(pointComboBox));
  }

  private String getSelectedVehicleName() {
    VehicleTableModel model = (VehicleTableModel) vehicleTable.getModel();
    return model.getDataAt(vehicleTable.getSelectedRow()).getVehicleName();
  }

  private List<String> getSelectedVehicleNames() {
    List<String> selectedVehicleNames = new ArrayList<>();
    VehicleTableModel model = (VehicleTableModel) vehicleTable.getModel();
    for (int selectedRow : vehicleTable.getSelectedRows()) {
      String selectedVehicleName = model.getDataAt(selectedRow).getVehicleName();
      selectedVehicleNames.add(selectedVehicleName);
    }
    return selectedVehicleNames;
  }

  private List<VehicleEntry> getSelectedVehicleEntries() {
    List<VehicleEntry> selectedEntries = new LinkedList<>();
    for (String selectedVehicleName : getSelectedVehicleNames()) {
      selectedEntries.add(vehicleEntryPool.getEntryFor(selectedVehicleName));
    }
    return selectedEntries;
  }

  @SuppressWarnings("deprecation")
  private void createDriverMenu() {
    driverMenu.removeAll();
    for (VehicleCommAdapterFactory factory : commAdapterRegistry.getFactories()) {
      // If there's one vehicle not supported by this factory the selection can't be attached to it
      boolean factorySupportsSelectedVehicles = getSelectedVehicleEntries().stream()
          .map(entry -> entry.getVehicle())
          .allMatch(vehicle -> factory.providesAdapterFor(vehicle));

      List<String> vehiclesToAttach = new ArrayList<>();
      if (factorySupportsSelectedVehicles) {
        vehiclesToAttach = getSelectedVehicleNames();
      }

      Action action = new AttachCommAdapterAction(factory.getAdapterDescription(),
                                                  vehiclesToAttach,
                                                  factory);
      JMenuItem menuItem = driverMenu.add(action);
      menuItem.setEnabled(factorySupportsSelectedVehicles);
    }
  }

  private void createPopupMenu() {
    // Find out how many vehicles (don't) have a driver attached.
    StatesCounts stateCounts = getCommAdapterStateCountsFor(vehicleEntryPool.getEntries().values());
    enableAllMenuItem.setEnabled(stateCounts.disabledCount > 0);
    disableAllMenuItem.setEnabled(stateCounts.enabledCount > 0);

    // Now do the same for those that are selected.
    stateCounts = getCommAdapterStateCountsFor(getSelectedVehicleEntries());
    enableAllSelectedMenuItem.setEnabled(stateCounts.disabledCount > 0);
    disableAllSelectedMenuItem.setEnabled(stateCounts.enabledCount > 0);
  }

  private StatesCounts getCommAdapterStateCountsFor(Collection<VehicleEntry> entries) {
    StatesCounts stateCounts = new StatesCounts();
    for (VehicleEntry entry : entries) {
      VehicleCommAdapter commAdapter = entry.getCommAdapter();
      if (commAdapter == null) {
        stateCounts.detachedCount++;
      }
      else {
        stateCounts.attachedCount++;
        if (commAdapter.isEnabled()) {
          stateCounts.enabledCount++;
        }
        else {
          stateCounts.disabledCount++;
        }
      }
    }
    return stateCounts;
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
        ResourceBundle bundle = ResourceBundle.getBundle("org.opentcs.kernel.controlcenter.vehicles.Bundle");
        listDisplayPanel = new JPanel();
        jScrollPane1 = new JScrollPane();
        vehicleTable = new JTable();
        vehicleDetailPanel = new JPanel();
        vehicleListPopupMenu = new JPopupMenu();
        driverMenu = new JMenu();
        noDriversMenuItem = new JMenuItem();
        jSeparator1 = new JSeparator();
        enableAllMenuItem = new JMenuItem();
        enableAllSelectedMenuItem = new JMenuItem();
        jSeparator4 = new JSeparator();
        disableAllMenuItem = new JMenuItem();
        disableAllSelectedMenuItem = new JMenuItem();

        //======== this ========
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

        //======== listDisplayPanel ========
        {
            listDisplayPanel.setBorder(new TitledBorder("Vehicles in model"));
            listDisplayPanel.setMaximumSize(new Dimension(464, 2147483647));
            listDisplayPanel.setMinimumSize(new Dimension(464, 425));
            listDisplayPanel.setLayout(new BorderLayout());

            //======== jScrollPane1 ========
            {

                //---- vehicleTable ----
                vehicleTable.setModel(new VehicleTableModel());
                vehicleTable.setComponentPopupMenu(vehicleListPopupMenu);
                vehicleTable.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        vehicleTableMouseClicked(e);
                    }
                });
                jScrollPane1.setViewportView(vehicleTable);
            }
            listDisplayPanel.add(jScrollPane1, BorderLayout.CENTER);
        }
        add(listDisplayPanel);

        //======== vehicleDetailPanel ========
        {
            vehicleDetailPanel.setBorder(new TitledBorder("Vehicle details"));
            vehicleDetailPanel.setPreferredSize(new Dimension(800, 23));
            vehicleDetailPanel.setLayout(new BorderLayout());
        }
        add(vehicleDetailPanel);

        //======== vehicleListPopupMenu ========
        {
            vehicleListPopupMenu.addPopupMenuListener(new PopupMenuListener() {
                @Override
                public void popupMenuCanceled(PopupMenuEvent e) {}
                @Override
                public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}
                @Override
                public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                    vehicleListPopupMenuPopupMenuWillBecomeVisible(e);
                }
            });

            //======== driverMenu ========
            {
                driverMenu.setText(bundle.getString("Driver"));
                driverMenu.addMenuListener(new MenuListener() {
                    @Override
                    public void menuCanceled(MenuEvent e) {}
                    @Override
                    public void menuDeselected(MenuEvent e) {}
                    @Override
                    public void menuSelected(MenuEvent e) {
                        driverMenuMenuSelected(e);
                    }
                });

                //---- noDriversMenuItem ----
                noDriversMenuItem.setText("No drivers available.");
                noDriversMenuItem.setEnabled(false);
                driverMenu.add(noDriversMenuItem);
            }
            vehicleListPopupMenu.add(driverMenu);
            vehicleListPopupMenu.add(jSeparator1);

            //---- enableAllMenuItem ----
            enableAllMenuItem.setText(bundle.getString("EnableAll"));
            enableAllMenuItem.addActionListener(e -> enableAllMenuItemActionPerformed(e));
            vehicleListPopupMenu.add(enableAllMenuItem);

            //---- enableAllSelectedMenuItem ----
            enableAllSelectedMenuItem.setText(bundle.getString("EnableSelected"));
            enableAllSelectedMenuItem.addActionListener(e -> enableAllSelectedMenuItemActionPerformed(e));
            vehicleListPopupMenu.add(enableAllSelectedMenuItem);
            vehicleListPopupMenu.add(jSeparator4);

            //---- disableAllMenuItem ----
            disableAllMenuItem.setText(bundle.getString("DisableAll"));
            disableAllMenuItem.addActionListener(e -> disableAllMenuItemActionPerformed(e));
            vehicleListPopupMenu.add(disableAllMenuItem);

            //---- disableAllSelectedMenuItem ----
            disableAllSelectedMenuItem.setText(bundle.getString("DisableSelected"));
            disableAllSelectedMenuItem.addActionListener(e -> disableAllSelectedMenuItemActionPerformed(e));
            vehicleListPopupMenu.add(disableAllSelectedMenuItem);
        }
    }// </editor-fold>//GEN-END:initComponents

  private void driverMenuMenuSelected(javax.swing.event.MenuEvent evt) {//GEN-FIRST:event_driverMenuMenuSelected
    createDriverMenu();
  }//GEN-LAST:event_driverMenuMenuSelected

  private void enableAllMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableAllMenuItemActionPerformed
    enableAllCommAdapters();
  }//GEN-LAST:event_enableAllMenuItemActionPerformed

  private void enableAllSelectedMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableAllSelectedMenuItemActionPerformed
    enableSelectedCommAdapters();
  }//GEN-LAST:event_enableAllSelectedMenuItemActionPerformed

  private void disableAllMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_disableAllMenuItemActionPerformed
    disableAllCommAdapters();
  }//GEN-LAST:event_disableAllMenuItemActionPerformed

  private void disableAllSelectedMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_disableAllSelectedMenuItemActionPerformed
    disableSelectedCommAdapters();
  }//GEN-LAST:event_disableAllSelectedMenuItemActionPerformed

  private void vehicleListPopupMenuPopupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent evt) {//GEN-FIRST:event_vehicleListPopupMenuPopupMenuWillBecomeVisible
    createPopupMenu();
  }//GEN-LAST:event_vehicleListPopupMenuPopupMenuWillBecomeVisible

  private void vehicleTableMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_vehicleTableMouseClicked
    if (evt.getClickCount() == 2) {
      int index = vehicleTable.getSelectedRow();
      if (index >= 0) {
        VehicleTableModel model = (VehicleTableModel) vehicleTable.getModel();
        VehicleEntry clickedEntry = model.getDataAt(index);
        DetailPanel detailPanel = (DetailPanel) vehicleDetailPanel.getComponent(0);
        detailPanel.attachToVehicle(clickedEntry);
      }
    }
  }//GEN-LAST:event_vehicleTableMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private JPanel listDisplayPanel;
    private JScrollPane jScrollPane1;
    private JTable vehicleTable;
    private JPanel vehicleDetailPanel;
    private JPopupMenu vehicleListPopupMenu;
    private JMenu driverMenu;
    private JMenuItem noDriversMenuItem;
    private JSeparator jSeparator1;
    private JMenuItem enableAllMenuItem;
    private JMenuItem enableAllSelectedMenuItem;
    private JSeparator jSeparator4;
    private JMenuItem disableAllMenuItem;
    private JMenuItem disableAllSelectedMenuItem;
    // End of variables declaration//GEN-END:variables
  // CHECKSTYLE:ON

  /**
   * Attaches adapters produced by a given factory to a set of vehicles when performed.
   */
  private class AttachCommAdapterAction
      extends AbstractAction {

    /**
     * The affected vehicles' entries.
     */
    private final List<String> vehicleNames;
    /**
     * The factory providing the communication adapter.
     */
    private final VehicleCommAdapterFactory factory;

    /**
     * Creates a new AttachCommAdapterAction.
     *
     * @param description A string describing the factory.
     * @param vehicleNames The affected vehicles' entries.
     * @param factory The factory providing the communication adapter.
     */
    private AttachCommAdapterAction(String description,
                                    List<String> vehicleNames,
                                    VehicleCommAdapterFactory factory) {
      super(description);
      this.vehicleNames = requireNonNull(vehicleNames, "vehicleNames");
      this.factory = requireNonNull(factory, "factory");
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
      for (String vehicleName : vehicleNames) {
        attachManager.attachAdapterToVehicle(vehicleName, factory);
      }
    }
  }

  private class StatesCounts {

    private int attachedCount;
    private int detachedCount;
    private int enabledCount;
    private int disabledCount;
  }
}
