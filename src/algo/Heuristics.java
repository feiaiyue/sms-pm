package algo;

import comn.Base;
import comn.Param;

import java.util.*;

public class Heuristics {
    Instance instance;
    int maxIter = 5;

    public Heuristics(Instance instance) {
        this.instance = instance;
    }


    /**
     *
     * @param block
     * @param a remove a
     * @param b remove b
     * @return after exchange operator, whether column's processing time fulfill the constraint
     */
    public boolean exchangeFeasible(Block block, int a, int b) {
        return (block.processingTime - instance.p[a] + instance.p[b]) <= instance.T;

    }

    /**
     * Constructs a solution based on provided blocks and their corresponding values.(LpSol)
     * @param blocks
     * @param values
     * @return a solution constructed by adding blocks with values greater than 0.5 and sorting(decreasing) the remaining jobs.
     */
    public Solution construct(ArrayList<Block> blocks, ArrayList<Double> values) {
        Solution heuristicsSol = new Solution();

        int nJobs = instance.nJobs;
        BitSet remainJobs = new BitSet(nJobs);
        remainJobs.set(0, nJobs);

        if (blocks != null) {
            for (int i = 0; i < blocks.size(); i++) {
                Block block = blocks.get(i);
                double value = values.get(i);
                if (value  > 0.5 + Base.EPS) {
                    boolean canAdd = true;
                    for (int job : block) {
                        if (!remainJobs.get(job)) {
                            if (Param.debug) {
                                System.out.println("job: " + job + "not remain");
                            }
                            canAdd = false;
                        }
                    }
                    // System.out.println("column's value > 0.5 : " + column + "value" + value);
                    if (canAdd) {
                        heuristicsSol.add(block);
                        for (int job : block) {
                            remainJobs.clear(job);
                        }
                    }

                }
            }
        }

        // System.out.println("after choose columns whose value > 0.5: " + remainJobs.toString());
        ArrayList<Integer> list = new ArrayList<>();
        for (int i = remainJobs.nextSetBit(0); i >= 0; i = remainJobs.nextSetBit(i + 1)) {
            list.add(i);
        }

        // Sort the remained jobs in descending order based on the values of instance.p[job]
        Collections.sort(list, Comparator.comparingInt(job -> -instance.p[job]));
        packRemains(heuristicsSol, list);
        if (Param.debug) {
            String constructSol = "=".repeat(30) + "Construct Initial Solution" + "=".repeat(30) + "\n"
                    + heuristicsSol.toString();
            System.out.println(constructSol);
        }
        return heuristicsSol;
    }

    public Solution packRemains(Solution solution, ArrayList<Integer> list) {
        BitSet colSize = new BitSet();
        for (int i = 0; i <  solution.size(); i++) {
            if (!solution.get(i).noMoreToPack(instance)) {
                colSize.set(i);
            }
        }

        for (Integer job : list) {
            boolean packed = false;

            for (int i = colSize.nextSetBit(0); i >= 0 && !packed; i = colSize.nextSetBit(i + 1)) {
                if (solution.get(i).canPackItem(instance, job)) {
                    solution.get(i).add(job, instance);
                    packed = true;

                    if (solution.get(i).noMoreToPack(instance)) {
                        colSize.clear(i);
                    }
                }
            }

            if (!packed) {
                Block newBlock = new Block();
                newBlock.add(job, instance);
                solution.add(newBlock);
                colSize.set(solution.size() - 1);
            }
        }
        solution.computeMakespan(instance);

        // System.out.println(solution.toString());

        return solution;
    }

    public Solution relocate(Solution solution) {
        Solution sol = new Solution(solution);

        Collections.sort(sol, Comparator.comparingInt(column -> -column.processingTime));


        for (int i = 0; i < sol.size(); i++) {
            if (!sol.get(i).noMoreToPack(instance)) {
                // j 从末尾开始
                for (int j = sol.size() - 1; j > i; j--) {
                    List<Integer> jobs = new ArrayList<>(sol.get(j)); // 创建副本，因为 sol(j) 后面会被修改
                    for (int job : jobs) {
                        if (sol.get(i).canPackItem(instance, job)) {
                            sol.get(j).remove(job, instance);
                            sol.get(i).add(job, instance);
                           /*  if (Param.debug) {
                                System.out.println("Column:" + j + " job:" + job  + " has been relocated to"
                                + " Column:" + i);
                                sol.removeIf(Column::isEmpty);
                                sol.computeMakespan(instance);
                                System.out.println("-".repeat(30) + "after relocate operator: " + "-".repeat(30));
                                System.out.println(sol.toString());
                                System.exit(-1);
                            } */
                        }
                    }
                }
            }
        }

        // 有些 column 空了就删掉
        sol.removeIf(Block::isEmpty);
        String afterRelocateSol = "-".repeat(30) + "after relocate operator: " + "-".repeat(30) + "\n"
                + sol.toString();
        if (Param.debug) {
            System.out.println(afterRelocateSol);
        }
        // 返回修改后的 Solution
        return sol;
    }

    public Solution exchange(Solution solution) {
        Solution sol = new Solution(solution);

        Collections.sort(sol, Comparator.comparingInt(column -> -column.processingTime));

        // TODO: 2024/1/7 if solution.get(i).processing time == instance.T 应该可以跳过吧
        for (int i = 0; i < sol.size(); i++) {
            if (sol.get(i).processingTime == instance.T) {
                continue;
            }
            for (int index1 = 0; index1 < sol.get(i).size(); index1++) {
                int a = sol.get(i).get(index1);
                for (int j = sol.size() - 1; j > i; j--) {
                    for (int index2 = 0; index2 < sol.get(j).size(); index2++) {
                        int b = sol.get(j).get(index2);
                        if (instance.p[a] < instance.p[b] && exchangeFeasible(sol.get(i), a, b)
                                && exchangeFeasible(sol.get(j), b, a)) {
                            String exchangeStr = "job " + a + "exchange with" + "job " + b + "\n"
                                    + "Column " + i + "+" + (instance.p[b] - instance.p[a]) + "\n"
                                    + "Column " + j + "-" + (instance.p[b] - instance.p[a]);
                            if (Param.debug) {
                                System.out.println(exchangeStr);
                            }
                            sol.get(i).set(index1, b);
                            sol.get(i).processingTime += instance.p[b] - instance.p[a];
                            sol.get(j).set(index2, a);
                            sol.get(j).processingTime += instance.p[a] - instance.p[b];
                        }
                    }
                }
            }
        }
        sol.computeMakespan(instance);
        String afterExchangeSol = "-".repeat(30) + "after exchange operator: " + "-".repeat(30) + "\n" + sol.toString();
        if (Param.debug) {
            System.out.println(afterExchangeSol);
        }
        return sol;
    }

    public Solution localSearch(Solution solution) {
        boolean diff = true;
        Solution solution1 = new Solution(solution);

        // 使用 while 循环替代 Scala 中的 while
        while (diff) {
            Solution solution2 = relocate(solution1);
            solution2 = exchange(solution2);
            // solution2 = ejectionChain(solution2);

            diff = solution2.makespan != solution1.makespan || solution2.equals(solution1);
            if (diff) {
                System.exit(-1);
                solution1 = new Solution(solution2);
            }
        }
        return solution1;
    }



    public Solution shake(Solution solution) {
        return solution;

    }

    public Solution solve(ArrayList<Block> blocks, ArrayList<Double> values) {
        long start = System.currentTimeMillis();


        Solution sol = construct(blocks, values);
        return sol;
        // sol = localSearch(sol);
        // return sol;

        // Solution bestSol = new Solution(sol);
        //
        // for (int iter = 0; iter < maxIter; iter++) {
        //     sol = shake(sol);
        //     sol = localSearch(sol);
        //     if (sol.makespan < bestSol.makespan) {
        //         bestSol = new Solution(sol);
        //     }
        //
        // }
        // return bestSol;
    }

}
