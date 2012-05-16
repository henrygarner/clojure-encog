(ns clojure-encog.training
(:import
       (org.encog.neural.networks.training CalculateScore)
       (org.encog.neural.networks.training.simple TrainAdaline)
       (org.encog.neural.networks.training.propagation.back  Backpropagation)
       (org.encog.neural.networks.training.propagation.manhattan ManhattanPropagation)              
       (org.encog.neural.networks.training.propagation.quick  QuickPropagation) 
       (org.encog.neural.networks.training.propagation.scg ScaledConjugateGradient)
       (org.encog.neural.networks.training.propagation.resilient ResilientPropagation)
       (org.encog.neural.networks.training.genetic NeuralGeneticAlgorithm)
       (org.encog.neural.networks.training.pnn TrainBasicPNN)
       (org.encog.neural.networks.training.nm  NelderMeadTraining)
       (org.encog.neural.networks.training.anneal NeuralSimulatedAnnealing)
       (org.encog.ml.data.basic BasicMLData BasicMLDataSet)
       (org.encog.ml.data.temporal TemporalMLDataSet)
       (org.encog.ml.data.folded FoldedDataSet)
       (org.encog.ml.train MLTrain)
       (org.encog.ml.svm.training SVMTrain)
       (org.encog.ml.svm SVM)
       (org.encog.ml MLRegression)
       (org.encog.neural.networks BasicNetwork)
       (org.encog.util.arrayutil NormalizeArray TemporalWindowArray)))



(defn make-data 
"Constructs a MLData object given some data which can be nested as well." 
[of-type data]
(condp = of-type
   :basic         (if (number? data) (BasicMLData. data) 
                                     (BasicMLData. (double-array data)))
   :basic-complex nil;;TODO
   :basic-dataset (BasicMLDataSet. (into-array (map double-array (first data))) 
                                   (into-array (map double-array (second data))))
   ;:temporal-dataset (TemporalMLDataSet. ) 
   :temporal-window (fn [window-size prediction-size]
                           (let [twa (TemporalWindowArray. window-size prediction-size)]
                           (do (. twa analyze (doubles data)) 
                               (. twa process (doubles data)))))
                           
                                                          
   ;:folded (FoldedDataSet.)
:else (throw (IllegalArgumentException. "Unsupported data model!"))
))

(defmacro judge 
"Consumer convenience for implementing the CalculateScore interface which is needed for genetic and simulated annealing training."
[minimize? & body]
`(proxy [CalculateScore] [] 
  (calculateScore [^MLRegression n#] ~@body) 
  (shouldMinimize [] ~minimize?)))

(defn make-trainer
"Constructs a training-method object."
[method]
(condp = method
       :simple     (fn [net tr-set learn-rate] (TrainAdaline.  net tr-set (if (nil? learn-rate) 2.0 learn-rate)))
       :back-prop  (fn [net tr-set] (Backpropagation. net tr-set))
       :manhattan  (fn [net tr-set learn-rate] (ManhattanPropagation. net tr-set learn-rate))
       :quick-prop (fn [net tr-set learn-rate] (QuickPropagation. net tr-set (if (nil? learn-rate) 2.0 learn-rate)))
       :genetic    (fn [net randomizer fit-fun minimize? pop-size mutation-percent mate-percent] 
                       (NeuralGeneticAlgorithm. net randomizer 
                                                       (judge minimize? fit-fun) 
                                                       pop-size mutation-percent mate-percent))
       :scaled-conjugent   (fn [net tr-set] (ScaledConjugateGradient. net tr-set))
       :pnn                (fn [net tr-set] (TrainBasicPNN. net tr-set))
       :annealing     (fn [net fit-fun minimize? startTemp stopTemp cycles] 
                      (NeuralSimulatedAnnealing. net (judge minimize? fit-fun) startTemp stopTemp cycles))
       :resilient-prop (fn [net tr-set]      (ResilientPropagation. net tr-set))
       :nelder-mead    (fn [net tr-set step] (NelderMeadTraining. net tr-set (if (nil? step) 100 step)))
       :svm            (fn [^SVM net tr-set] (SVMTrain. net tr-set))
 :else (throw (IllegalArgumentException. "Unsupported training method!"))      
))


;;usage: ((make-trainer :resilient-prop) (make-network blah-blah) some-data-set)
;;       ((make-trainer :genetic) (make-network blah-blah) some-data-set)


(defmacro genericTrainer [method & args]
`(fn [& details#] 
   (new ~method (first ~@args) ;the network
                (second ~@args);the training set 
                (rest (rest ~@args)))))
                
                               
(defn train 
"Does the actual training. This is a potentially lengthy and costly process so most type hints have been provided. Returns true or false depending on whether the error target was met within the iteration limit."
[^MLTrain method ^Double error-tolerance ^Integer limit & strategies] ;;eg: (new RequiredImprovementStrategy 5)
(when (seq strategies) (dotimes [i (count strategies)] 
                       (.addStrategy method (nth strategies i))))
(loop [epoch (int 1)]
(if (< limit epoch) false ;;failed to converge
(do (. method iteration)
    (println "Epoch #" epoch " Error:" (. method getError))    
(if-not (> (. method getError) 
           error-tolerance) true ;;succeeded to converge 
(recur (inc epoch)))))))


(defn normalize 
"Normalizes a seq (vector/list) of doubles (no nests) between high-end low-end and returns the normalized double array. Call seq on the result to convert it back to a clojure seq so it reads nicely on the repl." 
[data high-end low-end] 
(let [norm (NormalizeArray.)]
(do (. norm setNormalizedHigh high-end)
    (. norm setNormalizedLow  low-end)  
    (. norm process (double-array data)))))









 