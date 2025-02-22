package algo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * the class of Column need to be overwritten
 */
public class Solution extends ArrayList<Block> {
    public double makespan;


    public Solution() {
        this.makespan = 0;
    }

    public Solution(Solution other) {
        for (Block col : other) {
            this.add(col);
        }
        this.makespan = other.makespan;

    }



    public void computeMakespan(Instance instance) {
        if (this.size() == 0) {
            makespan = 0;
        } else {
            makespan = (instance.T + instance.t) * (this.size() - 1) +
                    this.get(this.size() - 1).processingTime;
        }

    }

    public boolean isFeasible(Instance instance) {
        for (Block block : this) {
            if (!block.isFeasible(instance)) {
                return false;
            }
        }

        // job must be processed once, use int[] instead of bitset
        int[] visit = new int[instance.nJobs];
        for (Block block : this) {
            for (int job : block) {
                visit[job]++;
            }
        }

        for (int num : visit) {
            if (num != 1) {
                System.err.println("ERROR : each job must be visit only once");
                return false;
            }
        }
        return true;
    }

    public void reconstruct(Instance instance){
        int[] visit = new int[instance.nJobs];
        List<Block> blocksToRemove = new ArrayList<>();

        for (Block block : this) {
            for (Iterator<Integer> it = block.iterator(); it.hasNext(); ) {
                int job = it.next();
                if (visit[job] > 0) {
                    it.remove(); // 从 Block 中移除作业
                    block.processingTime -= instance.p[job]; // 更新处理时间
                }
                visit[job]++;
            }
            if (block.isEmpty()) {
                blocksToRemove.add(block); // 如果 block 为空，则标记为待移除
            }
        }

        // 移除空的 blocks
        this.removeAll(blocksToRemove);

    }


    /**
     * the description of the String of solution
     *
     * @return the Str
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("The num of Batches: ").append(this.size() - 1).append("\n");
        sb.append("makespan: ").append(makespan).append("\n");
        sb.append("The jobs in each Batch ").append("\n");
        if (!this.isEmpty()) {
            for (int i = 0; i < this.size() - 1; i++) {
                sb.append("Batch" + i + ":");
                if (this.get(i) != null) {
                }
                sb.append(this.get(i)).append("\n");  // 使用制表符对齐
            }
            if (this.get(this.size() - 1) != null) {
                sb.append("The last Batch").append("\n").append(this.get(this.size() - 1));
            }
        }
        return sb.toString();
    }

    // TODO: 2024/1/7 check
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Solution solution = (Solution) o;

        return makespan == solution.makespan;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), makespan);
    }
}



