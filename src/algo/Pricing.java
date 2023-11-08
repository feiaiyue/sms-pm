package algo;

import comn.Base;
import comn.Param;
import gurobi.*;

import java.lang.constant.Constable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;


/**
 * dp to solve the pricing problem
 */
public class Pricing {
    Instance instance;
    ArrayList<Column> colVarPool;
    GRBEnv env;
    GRBModel model;

    GRBVar[] z;
    GRBConstr constrBlock;

    Node node;

    public Pricing(Instance instance, ArrayList<Column> colVarPool) throws GRBException {
        this.instance = instance;
        this.colVarPool = colVarPool;
        this.env = new GRBEnv();
        env.set(GRB.IntParam.OutputFlag, 0);
        env.set(GRB.IntParam.Seed, Base.SEED);
        env.set(GRB.IntParam.Threads, Param.nThreads);
        this.model = new GRBModel(env);
        this.z = new GRBVar[instance.nJobs];
        buildModel();
    }

    public void buildModel() throws GRBException {
        for (int i = 0; i < instance.nJobs; i++) {
            z[i] = model.addVar(0, 1, 0,GRB.BINARY, "z" + (i + 1));
        }
        GRBLinExpr expr = new GRBLinExpr();
        for (int i = 0; i < instance.nJobs; i++) {
            expr.addTerm(instance.p[i], z[i]);
        }
        constrBlock = model.addConstr(expr, GRB.LESS_EQUAL, instance.T, "constraint1");
    }

    public void set(Node node) throws GRBException {
        this.node = node;
        GRBConstr[] constrs = model.getConstrs();
        for (int i = 1; i < constrs.length; i++) {
            model.remove(constrs[i]);
        }

        // add constraints with andItems to the model
        for (int i = 0; i < node.andItems.length; i++) {
            if (node.removedItems.get(i) == true) {
                continue;
            }
            for (int j = 0; j < node.andItems[i].length - 1; j++) {
                int job1 = node.andItems[i][j];
                int job2 = node.andItems[i][j + 1];
                model.addConstr(z[job1], GRB.EQUAL, z[job2], "constraints2_" + (job1 + 1) + " & " + (job2 + 1));
            }
        }

        for (int i = 0; i < node.orItems.length; i++) {
            // 这样就不会加重复，node.orItmes[i][j]和node.orItems[j][i]代表同一个含义。
            for (int j = i + 1; j < node.orItems[i].length; j++) {
                if (node.orItems[i][j] == true) {
                    GRBLinExpr expr = new GRBLinExpr();
                    expr.addTerm(1 ,z[i]);
                    expr.addTerm(1, z[j]);
                    model.addConstr(expr, GRB.LESS_EQUAL, 1, "constraints3_" + (i + 1) + " | " + (j + 1));
                }

            }
        }
    }

    /**
     * solving the pricing model to solve the pricing problem
     *
     * @param dual the dual variables produced from RMP
     * @return new columns
     * @throws GRBException
     */
    public ArrayList<Column> genColumn1(double[] dual) throws GRBException {
        ArrayList<Column> res = new ArrayList<>();
        GRBLinExpr obj = new GRBLinExpr();
        for (int i = 0; i < instance.nJobs; i++) {
            obj.addTerm(dual[i], z[i]);
        }

        model.setObjective(obj, GRB.MAXIMIZE);
        model.update();
        model.optimize();

        // System.out.println(model.get(GRB.IntAttr.Status));
        if (model.get(GRB.IntAttr.Status) == GRB.INFEASIBLE) {
            model.computeIIS();
            // 这个可以把互相冲突的约束给写出来，就是造成模型不可行的约束是哪些
            model.write("model_child.ilp");
            System.out.println("thismodel is infeasible");
        }

        ArrayList<Integer> jobs = new ArrayList<>();
        for (int i = 0; i < instance.nJobs; i++) {
            if (z[i].get(GRB.DoubleAttr.X) == 1.0) {
                jobs.add(i);
            }
        }
        // System.out.println("(不一定加入到结果里去）新生成的列为" + jobs);
        double alpha = dual[instance.nJobs + 1];
        double beta = dual[instance.nJobs + 2];
        double reducedCost = instance.T + instance.t - alpha - beta;
        for (int job : jobs) {
            reducedCost -= dual[job];
        }
        if (node.nodeID != 2) {
            if (reducedCost < 0 && Math.abs(reducedCost) > Base.EPS) {
                Column x = new Column(instance, jobs);
                res.add(x);
            }
            return res;
        }

        if (node.nodeID == 2) {
            if (reducedCost < 0) {
                Column x = new Column(instance, jobs);
                res.add(x);
            }
        }
        return res;
    }


    /**
     * dynamic programming(2-dp) to solve pricing problem
     *
     * @param dual dual variables to constraints (for job occurrence time) in RMP（Restricted Master Problem）
     * @return a list of generated columns solved by pricing problem
     */
    public ArrayList<Column> genColumn2(double[] dual) {
        // System.out.println("---------------通过动态规划开始产生新的列-----------------------------");
        ArrayList<Column> res = new ArrayList<>();
        // TODO: 2023/10/7 using 1- dp to improve 2-dp
        double[][] dp = new double[instance.nJobs][instance.T + 1];
        for (int j = 0; j < instance.T + 1; j++) {
            if (j >= instance.p[0]) {
                dp[0][j] = dual[0];
            }
        }
        for (int i = 1; i < instance.nJobs; i++) {
            for (int j = 1; j < instance.T + 1; j++) {
                if (j >= instance.p[i]) {
                    dp[i][j] = Math.max(dp[i - 1][j - instance.p[i]] + dual[i], dp[i - 1][j]);
                } else {
                    dp[i][j] = dp[i - 1][j];
                }
            }
        }
        // TODO: 2023/9/25  对于dp中结果大于（T+t）的column都加进去
        ArrayList<Integer> jobs = new ArrayList<>();
        int leftT = instance.T;
        for (int i = instance.nJobs - 1; i > 0; i--) {
            if (dp[i][leftT] != dp[i - 1][leftT]) {
                leftT -= instance.p[i];
                jobs.add(i);
            }
        }
        if (leftT > 0 && dp[0][leftT] > 0) {
            jobs.add(0);
        }

        // System.out.println("新生成的列中所包含的jobs的编号为" + jobs);
        double alpha = dual[101];
        double beta = dual[102];
        double reducedCost = instance.T + instance.t - alpha - beta;
        for (int i = 0; i < jobs.size(); i++) {
            reducedCost -= dual[jobs.get(i)];
        }

        // System.out.println("reducedCost的值为" + reducedCost);

        if (reducedCost < 0 && Math.abs(reducedCost) > Base.EPS) {
            System.out.println("reducedCost的值为" + reducedCost);
            Column x = new Column(instance, jobs);
            res.add(x);
        }
        return res;
    }
    public void end() throws GRBException {
        model.dispose();
        env.dispose();
    }


}
