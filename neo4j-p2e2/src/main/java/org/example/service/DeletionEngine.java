package org.example.service;

import org.example.dependency.Cell;
import org.example.dependency.InstantiatedModel;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

@Service
public class DeletionEngine {

    public HashSet<Cell> optimalDelete(
            InstantiatedModel model,
            Cell deleted
    ) {

        for (var currLevels : model.treeLevels) {

            for (var currCell : currLevels) {

                var childrenEdges =
                        model.cell2Edge.get(currCell);

                currCell.cost = 1;

                if (childrenEdges != null) {

                    for (var edge : childrenEdges) {

                        long minCost =
                                Integer.MAX_VALUE;

                        Cell minCell = null;

                        for (var cell : edge) {

                            if (
                                    minCell == null ||
                                            cell.cost < minCost
                            ) {

                                minCell = cell;
                                minCost = cell.cost;
                            }
                        }

                        edge.minCell = minCell;

                        currCell.cost += minCost;
                    }
                }
            }
        }

        Queue<Cell> cellsToVisit =
                new LinkedList<>();

        cellsToVisit.add(deleted);

        HashSet<Cell> toDelete =
                new HashSet<>();

        toDelete.add(deleted);

        while (!cellsToVisit.isEmpty()) {

            var currCell = cellsToVisit.poll();

            var edges =
                    model.cell2Edge.get(currCell);

            if (edges != null) {

                for (var edge : edges) {

                    if (
                            toDelete.add(edge.minCell)
                    ) {

                        cellsToVisit.add(
                                edge.minCell
                        );
                    }
                }
            }
        }

        return toDelete;
    }
}