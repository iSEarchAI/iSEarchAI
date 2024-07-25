package br.otimizes.isearchai.learning;

import weka.clusterers.*;
import weka.core.DistanceFunction;
import weka.core.Instance;
import weka.core.Instances;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author WmfSystem
 * Class that encompasses the methods of clustering, such as K-Means, DBSCAN and OPTICS
 */
public class Clustering<T extends MLSolutionSet<MLSolution<MLElement>, MLElement>>  implements Serializable {

    private static final long serialVersionUID = 1L;

    private T resultFront;
    private ClusteringAlgorithm algorithm;
    private AbstractClusterer clusterer;
    private ArffExecution arffExecution;
    private DistanceFunction distanceFunction;
    private List<MLSolution> filteredMLSolutions = new ArrayList<>();
    private List<Integer> idsFilteredSolutions = new ArrayList<>();
    private Double indexToFilter = 1.0;
    private List<MLSolution> allMLSolutions = new ArrayList<>();
    private int numObjectives;
    private double[] min;
    private double[] max;

    /**
     * K-Means Parameters
     */
    private Integer numClusters;

    /**
     * DBSCAN and OPTICS Parameters
     */
    private Double epsilon = 0.3;
    private Integer minPoints = 3;
    private Integer maxIterations;

    public Clustering() {
    }

    @SuppressWarnings("unchecked")
    private Class<T> clazz(T resultFront) {
        return (Class<T>) resultFront.getClass();
    }

    public Clustering(T resultFront, ClusteringAlgorithm algorithm) {
//        TODO Willian
        if (resultFront.size() == 0)
            return;
        try {
            this.resultFront = (T) clazz(resultFront).getConstructors()[0].newInstance(resultFront.size());
            this.resultFront.setSolutions(resultFront.getSolutions());
            this.resultFront.setSolutions(resultFront.getSolutions());
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        this.algorithm = algorithm;

        this.arffExecution = new ArffExecution(resultFront.writeObjectivesToMatrix());
        this.numObjectives = this.resultFront.get(0).numberOfObjectives();
        min = new double[this.numObjectives];
        max = new double[this.numObjectives];
        for (int i = 0; i < numObjectives; i++) {
            min[i] = Double.MAX_VALUE;
            max[i] = Double.MIN_VALUE;
        }

        resultFront.forEach(r -> {
            for (int i = 0; i < this.numObjectives; i++) {
                if (r.getObjective(i) < min[i]) min[i] = r.getObjective(i);
                if (r.getObjective(i) > max[i]) max[i] = r.getObjective(i);
            }
        });
    }

    public Double np(Integer num) {
        return (-Math.log(1 - num)) / num;
    }

    /**
     * Execution Method
     *
     * @return Solution Set - Best performing cluster with another solutions (filteredSolutions)
     * @throws Exception Default Exception
     */
    public MLSolutionSet run() throws Exception {
        switch (algorithm) {
            case KMEANS:
                return kMeans();
            case DBSCAN:
                return dbscan();
            case OPTICS:
                return optics();
        }
        return null;
    }

    /**
     * K-Means Execution Method
     *
     * @return Solution Set
     * @throws Exception Default Exception
     */
    public MLSolutionSet kMeans() throws Exception {
        clusterer = new SimpleKMeans();
        getKMeans().setSeed(arffExecution.getAttributes().length);
        getKMeans().setPreserveInstancesOrder(true);
        if (distanceFunction != null)
            getKMeans().setDistanceFunction(distanceFunction);
        getKMeans().setNumClusters(getNumClusters());
        if (maxIterations != null)
            getKMeans().setMaxIterations(maxIterations);
        getKMeans().buildClusterer(arffExecution.getDataWithoutClass());

        return getFilteredSolutionSet();
    }

    /**
     * DBSCAN Execution Method
     * - Observations:
     * The only measure that changes the solution is as follows:
     * getDBSCAN().setOptions(new String[]{"-A", "weka.core.ManhattanDistance"});
     *
     * @return Solution Set
     * @throws Exception
     */
    public MLSolutionSet dbscan() throws Exception {
        clusterer = new DBSCAN();
        getDBSCAN().setMinPoints(getMinPoints());
        getDBSCAN().setEpsilon(getEpsilon());
        getDBSCAN().buildClusterer(arffExecution.getDataWithoutClass());

        return getFilteredSolutionSet();
    }

    /**
     * Method not completed, because in the current version of Weka, the OPTICS does not present br.otimizes.oplatool.core.jmetal4.results
     *
     * @return Nothing
     * @throws Exception Default Exception
     */
    public MLSolutionSet optics() throws Exception {
        clusterer = new OPTICS();
        getOPTICS().setShowGUI(false);
        getOPTICS().buildClusterer(arffExecution.getDataWithoutClass());
        return getFilteredSolutionSet();
    }

    private Double[] doubleArray(double[] doubles) {
        Double[] d = {};
        for (int i = 0; i < doubles.length; i++) {
            d[i] = doubles[i];
        }
        return d;
    }

    /**
     * Filtered Solution Set by attribute indexToFilter
     *
     * @return Solution Set Filtered
     * @throws Exception Default Exception
     */
    private MLSolutionSet getFilteredSolutionSet() throws Exception {
        if (clusterer instanceof SimpleKMeans) {
            getKMeans().getClusterCentroids().sort((o1, o2) -> {
                double[] doubles1 = o1.toDoubleArray();
                double[] doubles2 = o2.toDoubleArray();
                return compareEuclidianDistance(doubles2, doubles1);
            });
        }


        double[] assignments = getClusterEvaluation().getClusterAssignments();

        ArrayList<MLSolution> selected = new ArrayList<>();
        for (int i = 0; i < assignments.length; i++) {
            resultFront.get(i).setClusterId(assignments[i]);
            allMLSolutions.add(resultFront.get(i));
            if (assignments[i] < getIndexToFilter() && assignments[i] >= 0) {
                selected.add(resultFront.get(i));
            }
        }

        for (int i = 0; i < this.resultFront.size(); i++) {
            if (!selected.contains(this.resultFront.get(i))) {
                if (assignments[i] == -1) {
                    resultFront.get(i).setClusterNoise(true);
                }
                idsFilteredSolutions.add(i);
                filteredMLSolutions.add(resultFront.get(i));
            }
        }

        Collections.reverse(idsFilteredSolutions);
        idsFilteredSolutions.forEach(resultFront::remove);
        return resultFront;
    }

    private int compareEuclidianDistance(double[] doubles1, double[] doubles2) {
        Double dist1 = 0.0;
        Double dist2 = 0.0;
        for (int i = 0; i < doubles1.length; i++) {
            dist1 += Math.pow(doubles1[i] - min[i], 2);
            dist2 += Math.pow(doubles2[i] - min[i], 2);
        }
        return dist2.compareTo(dist1);
    }

    public double euclidianDistance(double[] doubles1, double[] doubles2) {

        Double somatorio = 0.0;
        for (int i = 0; i < doubles1.length; i++) {
            somatorio += Math.pow(doubles1[i] - doubles2[i], 2);
        }
        return Math.sqrt(somatorio);
    }

    public double euclidianDistance(MLSolution MLSolution) {
        return euclidianDistance(MLSolution.getObjectives(), min);
    }

    /**
     * Cluster Evaluation Object for analysis of br.otimizes.oplatool.core.jmetal4.results
     *
     * @return Clustes Evaluation Objetc
     * @throws Exception Default Exception
     */
    public ClusterEvaluation getClusterEvaluation() throws Exception {
        ClusterEvaluation clusterEvaluation = new ClusterEvaluation();
        clusterEvaluation.setClusterer(clusterer);
        clusterEvaluation.evaluateClusterer(arffExecution.getDataWithoutClass());
        return clusterEvaluation;
    }


    public SilhouetteIndex getSilhouetteIndex() throws Exception {
        SilhouetteIndex silhouetteIndex = new SilhouetteIndex();
        silhouetteIndex.evaluate(this.getClusterer(), ((SimpleKMeans) this.getClusterer()).getClusterCentroids(),
                this.getArffExecution().getDataWithoutClass(), this.distanceFunction);
        return silhouetteIndex;
    }

    /**
     * Cast the Clusterer to SimpleKMeans Object
     *
     * @return SimpleKMeans Object
     */
    public SimpleKMeans getKMeans() {
        return ((SimpleKMeans) clusterer);
    }

    /**
     * Cast the Clusterer to DBSCAN Object
     *
     * @return DBSCAN Object
     */
    public DBSCAN getDBSCAN() {
        return ((DBSCAN) clusterer);
    }

    /**
     * Cast the Clusterer to OPTICS Object
     *
     * @return OPTICS Object
     */
    public OPTICS getOPTICS() {
        return ((OPTICS) clusterer);
    }

    public MLSolutionSet getResultFront() {
        return resultFront;
    }

    public void setResultFront(T resultFront) {
        this.resultFront = resultFront;
    }

    public ClusteringAlgorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(ClusteringAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    public AbstractClusterer getClusterer() {
        return clusterer;
    }

    public void setClusterer(AbstractClusterer clusterer) {
        this.clusterer = clusterer;
    }

    public ArffExecution getArffExecution() {
        return arffExecution;
    }

    public void setArffExecution(ArffExecution arffExecution) {
        this.arffExecution = arffExecution;
    }

    /**
     * https://stats.stackexchange.com/questions/55215/way-to-determine-best-number-of-clusters-weka
     * https://en.wikipedia.org/wiki/Determining_the_number_of_clusters_in_a_data_set
     *
     * @return number of clusters
     */
    public int getNumClusters() {
        int i = numClusters != null ? numClusters : Math.toIntExact(Math.round(Math.pow((resultFront.size() / 2), 0.6)));
        if (i == 0) return 1;
        return i;
    }

    public int getGeneratedClusters() throws Exception {
        if (getClusterer() instanceof SimpleKMeans) return ((SimpleKMeans) getClusterer()).getNumClusters();
        else return getClusterer().numberOfClusters();
    }

    public void setNumClusters(Integer numClusters) {
        this.numClusters = numClusters;
    }

    public Integer getMinPoints() {
        return minPoints;
    }

    public void setMinPoints(Integer minPoints) {
        this.minPoints = minPoints;
    }

    public Double getEpsilon() {
        return epsilon;
    }

    public void setEpsilon(Double epsilon) {
        this.epsilon = epsilon;
    }

    public List<MLSolution> getFilteredSolutions() {
        return filteredMLSolutions;
    }



    public void setFilteredSolutions(List<MLSolution> filteredMLSolutions) {
        this.filteredMLSolutions = filteredMLSolutions;
    }

    /**
     * Get Index to Filter
     * Set by default: 1 -> K-Means, 2 -> DBSCAN and OPTICS
     *
     * @return Index to Filter Value
     */
    public double getIndexToFilter() {
        return indexToFilter;
    }

    public void setIndexToFilter(double indexToFilter) {
        this.indexToFilter = indexToFilter;
    }

    public List<Integer> getIdsFilteredSolutions() {
        return idsFilteredSolutions;
    }

    public void setIdsFilteredSolutions(List<Integer> idsFilteredSolutions) {
        this.idsFilteredSolutions = idsFilteredSolutions;
    }


    public DistanceFunction getDistanceFunction() {
        return distanceFunction;
    }

    public void setDistanceFunction(DistanceFunction distanceFunction) {
        this.distanceFunction = distanceFunction;
    }

    public List<MLSolution> getAllSolutions() {
        return allMLSolutions;
    }

    public void setAllSolutions(List<MLSolution> allMLSolutions) {
        this.allMLSolutions = allMLSolutions;
    }

    public List<MLSolution> getSolutionsByClusterWithMinObjective(int objectiveIndex) {
        return getSolutionsByClusterId(getMinClusterByObjective(objectiveIndex));
    }

    public List<MLSolution> getSolutionsByClusterId(double clusterId) {
        return allMLSolutions.stream().filter(s -> s.getClusterId() == clusterId).collect(Collectors.toList());
    }

    /**
     * If SimpleKMeans, returns the min centroid value by objective index, else, in case of DBSCAN that dont have
     * Centroid values, is verified the min value in the solutions
     * On DBSCAN, the clusterId -1 indicates noise
     *
     * @param objectiveIndex Objective array index
     * @return Min value by objective
     */
    public double getMinClusterByObjective(int objectiveIndex) {
        double min = Double.MAX_VALUE;
        double minCluster = 0.0;

        if (clusterer instanceof SimpleKMeans) {
            Instances clusterCentroids = getKMeans().getClusterCentroids();
            for (int i = 0; i < clusterCentroids.size(); i++) {
                Instance instance = clusterCentroids.get(i);
                if (instance.toDoubleArray()[objectiveIndex] <= min) {
                    min = instance.toDoubleArray()[objectiveIndex];
                    minCluster = i;
                }
            }
        } else {
            for (MLSolution allMLSolution : allMLSolutions) {
                if (allMLSolution.getObjective(objectiveIndex) <= min
                        && allMLSolution.getClusterId() != -1) {
                    min = allMLSolution.getObjective(objectiveIndex);
                    minCluster = allMLSolution.getClusterId();
                }
            }
        }
        return minCluster;
    }

    public int getNumObjectives() {
        return numObjectives;
    }

    public void setNumObjectives(int numObjectives) {
        this.numObjectives = numObjectives;
    }

    public double[] getMin() {
        return min;
    }

    public void setMin(double[] min) {
        this.min = min;
    }

    public double[] getMax() {
        return max;
    }

    public void setMax(double[] max) {
        this.max = max;
    }

    public Integer getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(Integer maxIterations) {
        this.maxIterations = maxIterations;
    }
}
