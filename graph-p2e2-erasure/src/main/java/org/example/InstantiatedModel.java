package org.example;

import org.example.GraphDependencyRules.Cell;

import java.sql.SQLException;
import java.util.*;

public class InstantiatedModel {
    HashMap<Cell, ArrayList<Cell.HyperEdge>> cell2Edge = new HashMap<>();
    HashMap<Cell, Long> instantiationTime = new HashMap<>();
    LinkedList<HashSet<Cell>> treeLevels = new LinkedList<>();
    HashMap<Cell, HashSet<Cell>> cell2Parents = new HashMap<>();
    long modelConstructionTime = 0L;

    static boolean containsParent(Cell.HyperEdge edge, HashSet<Cell> parents) {
        for (var cell : edge) {
            if (parents.contains(cell)) {
                return true;
            }
        }
        return false;
    }

    public InstantiatedModel(Cell deleted, Instantiator instantiator) throws SQLException {
        this(List.of(deleted), instantiator);
    }

    public InstantiatedModel(List<Cell> deletedCells, Instantiator instantiator) throws SQLException {
//        var start = System.nanoTime(); //? Starts timer
//        HashMap<Cell, Cell> cell2Identity = new HashMap<>(); //! deduplication
//        var instantiatedCells = new HashSet<Cell>(); // To prevent reprocessing the same cell
//
//        //! Sort on the ascending order of insertion times (compareTo(Cell cell) is defined in Cell.java)
//        if (deletedCells.size() > 1) {
//            Collections.sort(deletedCells);
//        }
//
//        //! Initial root map to themselves in the identity map
//        for (var deleted : deletedCells) {
//            cell2Identity.put(deleted, deleted);
//        }
//
//        for (var deleted : deletedCells) { //! Process each root cell
//            //! Continue if Cell already handled
//            if (!instantiatedCells.add(deleted)) {
//                continue;
//            }
//
//            HashSet<Cell> nextLevel = new HashSet<>();
//            HashSet<Cell> currLevel = new HashSet<>();
//            cell2Parents.put(deleted, new HashSet<>(0)); // Root has no parents
//            currLevel.add(deleted); // Start traversal from the root
//
//            while (!currLevel.isEmpty()) { // BFS style expansion loop
//                //! Temporary parent mapping for this level only, Merged later
//                HashMap<Cell, HashSet<Cell>> localCell2Parents = new HashMap<>();
//
//                //! Iterate through the cells in the current level
//                for (var curr : currLevel) { //! curr is a HashSet<Cell>
//                    var instantiationStart = System.nanoTime(); //Start timeing expansion of this cell
//                    var result = instatiator.instantiateAttachedCells(curr, deleted.insertionTime);
//                    instantiationTime.put(curr, System.nanoTime() - instantiationStart);
//
//                    for (var edge : result) { //! results is an arraylist of hyperedges
//                        if (!containsParent(edge, cell2Parents.get(curr))) { //! if the edge doesn't contain any parent
//                            var cellIter = edge.iterator(); //! Creates an iterator over the set of cells
//                            var newCells = new ArrayList<Cell>(edge.size()); //! Hold canonical representations
//
//                            while (cellIter.hasNext()) { //! Loop through the Cells in an edge
//                                var cell = cellIter.next();
//
//                                var unifiedCell = cell2Identity.get(cell); //! Get the identity of the cell
//                                if (unifiedCell == null) { //! if the cell doesn't exist already, put it to the hashmap
//                                    cell2Identity.put(cell, cell);
//                                    unifiedCell = cell;
//                                } else {  //! if the cell already exists
//                                    cellIter.remove(); //! Remove it from teh iterator (Edge)
//                                    newCells.add(unifiedCell); //! Add it to the newCells arraylist
//                                }
//                                localCell2Parents.computeIfAbsent(unifiedCell, a -> new HashSet<>()).add(curr); //! add unifiedCell -> curr to the cap
//
//                                //! New unseen cells are added to the nextLevel to be visited the next iteration
//                                if (instantiatedCells.add(unifiedCell)) { //! if absent add unifiedCell to the nextLevel
//                                    nextLevel.add(unifiedCell);
//                                }
//                            }
//                            edge.addAll(newCells); //! Add all the newCells cells(canonical cells) to the edge
//                            cell2Edge.computeIfAbsent(curr, a -> new ArrayList<>()).add(edge); //! Add curr -> cell2Edge to the map
//                        }
//                    }
//                }
//                for (var entry : localCell2Parents.entrySet()) {
//                    cell2Parents.merge(entry.getKey(), entry.getValue(), (a, b) -> {
//                        a.addAll(b);
//                        return a;
//                    });
//                }
//                treeLevels.addFirst(currLevel);
//                currLevel = nextLevel;
//                nextLevel = new HashSet<>();
//            }
//
//            //! Time between the start and the end
//            modelConstructionTime = System.nanoTime() - start;
//        }
    }
}
