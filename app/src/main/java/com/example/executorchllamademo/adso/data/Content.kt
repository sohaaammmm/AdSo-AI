package com.example.executorchllamademo.adso.data

import org.json.JSONArray
import org.json.JSONObject

/** Course content. Three courses ship with the app; learners can add their own (written by Llama). */

data class Topic(val id: String, val name: String, val goal: String)

data class CheckQuestion(val topicId: String, val text: String, val options: List<String>, val answer: Int) {
    /** The last option is always "Not sure yet". */
    val notSureIndex get() = options.lastIndex
}

data class QuizQuestion(val text: String, val options: List<String>, val answer: Int, val why: String)

/** Where the learner stands on a topic. */
enum class SkillLevel(val label: String, val fraction: Float) {
    STRONG("Strong", 0.88f),
    SHAKY("Needs work", 0.42f),
    NEW("New to you", 0.12f),
}

enum class SessionKind { LEARN, PRACTICE, QUIZ, REVIEW;
    val label get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

data class Course(
    val id: String,
    val title: String,
    val topics: List<Topic>,
    val check: List<CheckQuestion>,
    /** Fallback questions when Llama can't write one. Custom courses have none. */
    val bank: Map<String, List<QuizQuestion>> = emptyMap(),
    val custom: Boolean = false,
) {
    fun topicsJson(): String = JSONArray().apply { topics.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("goal", it.goal)) } }.toString()
    fun checkJson(): String = JSONArray().apply {
        check.forEach { q -> put(JSONObject().put("topic", q.topicId).put("q", q.text).put("answer", q.answer).put("options", JSONArray(q.options))) }
    }.toString()

    companion object {
        fun fromJson(id: String, title: String, topicsJson: String, checkJson: String): Course? = try {
            val t = JSONArray(topicsJson)
            val c = JSONArray(checkJson)
            Course(
                id, title,
                (0 until t.length()).map { t.getJSONObject(it).let { o -> Topic(o.getString("id"), o.getString("name"), o.optString("goal")) } },
                (0 until c.length()).map { c.getJSONObject(it).let { o ->
                    val opts = o.getJSONArray("options")
                    CheckQuestion(o.getString("topic"), o.getString("q"), (0 until opts.length()).map { i -> opts.getString(i) }, o.getInt("answer"))
                } },
                custom = true,
            )
        } catch (_: Exception) { null }
    }
}

private const val NOT_SURE = "Not sure yet"

private fun q(text: String, options: List<String>, answer: Int, why: String) = QuizQuestion(text, options, answer, why)

/** Every course the app knows about, built-in and custom. */
object Courses {
    val dataStructures = Course(
        "ds", "Data Structures",
        listOf(
            Topic("arr", "Arrays and complexity", "Explain O(1) vs O(n) with two examples"),
            Topic("ll", "Linked lists", "Reverse a list on paper"),
            Topic("sq", "Stacks and queues", "Score 80% on the adaptive quiz"),
            Topic("tr", "Trees", "Trace an in-order walk"),
            Topic("gr", "Graphs", "Run BFS by hand on a 6-node graph"),
        ),
        listOf(
            CheckQuestion("arr", "How long does it take to read the 5th item of an array?", listOf("O(1), constant time", "O(n), it walks the array", "O(log n)", NOT_SURE), 0),
            CheckQuestion("ll", "To add a node at the head of a singly linked list, you…", listOf("Shift every node one place right", "Point the new node at the old head", "Sort the list first", NOT_SURE), 1),
            CheckQuestion("sq", "Undo in a text editor is usually built with a…", listOf("Queue", "Stack", "Graph", NOT_SURE), 1),
            CheckQuestion("tr", "In a binary search tree, keys smaller than the root go…", listOf("To the left", "To the right", "Back to the root", NOT_SURE), 0),
            CheckQuestion("gr", "Breadth-first search visits nodes…", listOf("Level by level from the start", "Deepest first", "In sorted order", NOT_SURE), 0),
        ),
        mapOf(
            "arr" to listOf(
                q("Searching an unsorted array for a value takes…", listOf("O(1)", "O(n)", "O(log n)", "O(n²)"), 1, "You may have to look at every item once."),
                q("Binary search needs the array to be…", listOf("Sorted", "Short", "Full of unique items", "Stored as a list"), 0, "It halves a sorted range each step."),
                q("Inserting at the front of an array of n items costs…", listOf("O(1)", "O(log n)", "O(n)", "Nothing"), 2, "Every item shifts one place right."),
            ),
            "ll" to listOf(
                q("Getting the k-th node of a linked list takes…", listOf("O(1)", "O(k)", "O(log k)", "O(k²)"), 1, "You follow k pointers from the head."),
                q("A doubly linked node stores…", listOf("Only next", "Next and previous", "An index", "Two values"), 1, "It links both ways, so you can walk backwards."),
                q("Deleting the head node of a singly linked list costs…", listOf("O(1)", "O(n)", "O(log n)", "It is not allowed"), 0, "Move head to head.next and you are done."),
            ),
            "sq" to listOf(
                q("Which structure removes items in the order they were added (first in, first out)?", listOf("Stack", "Queue", "Binary tree", "Hash table"), 1, "A queue is first in, first out, like a line at a counter."),
                q("In a stack, which item do you remove first?", listOf("The oldest one", "The newest one", "The middle one", "The smallest one"), 1, "A stack is last in, first out: the newest item comes off the top."),
                q("Which operation adds an item to the top of a stack?", listOf("enqueue", "pop", "push", "peek"), 2, "push adds to the top, pop removes from it, peek only looks."),
            ),
            "tr" to listOf(
                q("An in-order walk of a binary search tree gives keys in…", listOf("Sorted order", "Reverse insertion order", "Random order", "Level order"), 0, "Left subtree, node, right subtree: smallest to largest."),
                q("A node with no children is called a…", listOf("Root", "Leaf", "Branch", "Edge"), 1, "Leaves are the ends of the tree."),
                q("The height of a balanced tree with n nodes grows like…", listOf("n", "log n", "n²", "1"), 1, "Each level doubles the number of nodes it can hold."),
            ),
            "gr" to listOf(
                q("BFS usually uses which helper structure?", listOf("Stack", "Queue", "Heap", "Tree"), 1, "The queue makes it visit nodes level by level."),
                q("A graph where edges have a direction is…", listOf("Directed", "Weighted", "Complete", "Sparse"), 0, "Each edge goes from one node to another, not both ways."),
                q("DFS goes…", listOf("Level by level", "As deep as it can, then back", "In sorted order", "To the nearest node only"), 1, "Depth-first follows one path to the end before backtracking."),
            ),
        ),
    )

    val discreteMaths = Course(
        "dm", "Discrete Mathematics",
        listOf(
            Topic("dm.sets", "Sets and logic", "Simplify a set expression with a Venn diagram"),
            Topic("dm.proof", "Proofs and induction", "Prove a sum formula by induction"),
            Topic("dm.count", "Counting", "Solve 3 permutation and combination problems"),
            Topic("dm.rel", "Relations and functions", "Tell injective and surjective functions apart"),
            Topic("dm.graph", "Graph theory basics", "Find an Euler path by hand"),
        ),
        listOf(
            CheckQuestion("dm.sets", "If A = {1, 2, 3} and B = {2, 3, 4}, what is A ∩ B?", listOf("{1, 2, 3, 4}", "{2, 3}", "{1, 4}", NOT_SURE), 1),
            CheckQuestion("dm.proof", "In proof by induction, after the base case you show…", listOf("If it holds for k, it holds for k + 1", "It holds for every even number", "The opposite is false", NOT_SURE), 0),
            CheckQuestion("dm.count", "In how many orders can 3 people stand in a line?", listOf("3", "9", "6", NOT_SURE), 2),
            CheckQuestion("dm.rel", "A function where no two inputs share an output is…", listOf("Surjective", "Injective (one-to-one)", "Constant", NOT_SURE), 1),
            CheckQuestion("dm.graph", "A connected graph has an Euler circuit when every vertex has…", listOf("Even degree", "Odd degree", "Degree 1", NOT_SURE), 0),
        ),
        mapOf(
            "dm.sets" to listOf(
                q("A ∪ B contains…", listOf("Items in both A and B", "Items in A or B", "Items in A but not B", "No items"), 1, "Union keeps everything that is in either set."),
                q("If p is true and q is false, p ∧ q is…", listOf("True", "False", "Undefined", "Both"), 1, "AND is true only when both parts are true."),
            ),
            "dm.proof" to listOf(
                q("The base case of an induction proof usually checks…", listOf("n = 1 (or the smallest n)", "n = k + 1", "Every n at once", "A counterexample"), 0, "You first show the statement holds at the start."),
                q("A proof by contradiction starts by assuming…", listOf("The statement is true", "The opposite of the statement", "Nothing", "The base case"), 1, "You assume the opposite and reach something impossible."),
            ),
            "dm.count" to listOf(
                q("How many ways to choose 2 of 4 friends (order doesn't matter)?", listOf("6", "8", "12", "4"), 0, "C(4, 2) = 4·3 / 2 = 6."),
                q("How many 3-letter codes from A–Z if letters can repeat?", listOf("26³", "26 · 25 · 24", "3²⁶", "78"), 0, "Each of the 3 places has 26 choices."),
            ),
            "dm.rel" to listOf(
                q("A function where every output is hit by some input is…", listOf("Injective", "Surjective (onto)", "Partial", "Inverse"), 1, "Onto means no output is left out."),
                q("A relation that is reflexive, symmetric and transitive is…", listOf("A partial order", "An equivalence relation", "A function", "A tree"), 1, "Those three properties define equivalence."),
            ),
            "dm.graph" to listOf(
                q("The sum of all vertex degrees equals…", listOf("The number of edges", "Twice the number of edges", "The number of vertices", "Zero"), 1, "Each edge adds 1 to two vertices."),
                q("A tree with n vertices has how many edges?", listOf("n", "n − 1", "n + 1", "2n"), 1, "Trees are connected with no cycles: exactly n − 1 edges."),
            ),
        ),
    )

    val python = Course(
        "py", "Python Programming",
        listOf(
            Topic("py.basics", "Variables and types", "Predict the type of 5 expressions"),
            Topic("py.flow", "Conditions and loops", "Write a loop that sums a list"),
            Topic("py.func", "Functions", "Write a function with a default argument"),
            Topic("py.coll", "Lists and dictionaries", "Count words with a dictionary"),
            Topic("py.oop", "Classes and objects", "Write a class with two methods"),
        ),
        listOf(
            CheckQuestion("py.basics", "What is type(3 / 2) in Python 3?", listOf("int", "float", "str", NOT_SURE), 1),
            CheckQuestion("py.flow", "How many times does  for i in range(3):  run?", listOf("3", "2", "4", NOT_SURE), 0),
            CheckQuestion("py.func", "What does a function return if it has no return statement?", listOf("0", "An error", "None", NOT_SURE), 2),
            CheckQuestion("py.coll", "How do you get the value for key 'a' in a dict d?", listOf("d.a", "d['a']", "d(a)", NOT_SURE), 1),
            CheckQuestion("py.oop", "Inside a method,  self  refers to…", listOf("The object the method is called on", "The parent class", "A global variable", NOT_SURE), 0),
        ),
        mapOf(
            "py.basics" to listOf(
                q("What does  len(\"hello\")  return?", listOf("4", "5", "6", "\"hello\""), 1, "len counts the characters: h, e, l, l, o."),
                q("Which of these is a valid variable name?", listOf("2nd_place", "my-score", "total_marks", "class"), 2, "Names can't start with a digit, contain '-', or be keywords."),
            ),
            "py.flow" to listOf(
                q("What does  break  do inside a loop?", listOf("Skips one turn", "Stops the loop", "Restarts the loop", "Nothing"), 1, "break leaves the loop immediately."),
                q("Which runs while x is less than 5?", listOf("while x < 5:", "for x < 5:", "if x < 5 loop:", "repeat x < 5:"), 0, "while repeats as long as its condition is true."),
            ),
            "py.func" to listOf(
                q("In  def greet(name=\"Priya\"),  \"Priya\" is a…", listOf("Return value", "Default argument", "Global", "Keyword"), 1, "It is used when no name is passed."),
                q("What does  return  do?", listOf("Prints a value", "Ends the function and gives back a value", "Repeats the function", "Imports a module"), 1, "return sends a value back to the caller."),
            ),
            "py.coll" to listOf(
                q("What is  [1, 2, 3][-1]?", listOf("1", "3", "-1", "An error"), 1, "Index -1 is the last item."),
                q("Which adds 4 to the end of list a?", listOf("a.add(4)", "a.append(4)", "a.push(4)", "a + 4"), 1, "Lists use append to add at the end."),
            ),
            "py.oop" to listOf(
                q("Which method runs when you create an object?", listOf("__init__", "__str__", "create", "main"), 0, "__init__ sets up a new object."),
                q("A class that takes features from another class uses…", listOf("Inheritance", "Recursion", "Iteration", "Import"), 0, "class Dog(Animal): inherits from Animal."),
            ),
        ),
    )

    val operatingSystems = Course(
        "os", "Operating Systems",
        listOf(
            Topic("os.proc", "Processes and threads", "Explain process vs thread with an example"),
            Topic("os.sched", "CPU scheduling", "Work out FCFS and Round Robin waiting times"),
            Topic("os.sync", "Synchronisation and deadlock", "Name the four deadlock conditions"),
            Topic("os.mem", "Memory and paging", "Translate a logical address with a page table"),
            Topic("os.fs", "File systems", "Compare contiguous and indexed allocation"),
        ),
        listOf(
            CheckQuestion("os.proc", "Threads of the same process share…", listOf("Their stack", "Their memory (address space)", "Nothing", NOT_SURE), 1),
            CheckQuestion("os.sched", "Round Robin scheduling gives each process…", listOf("A fixed time slice in turn", "The CPU until it finishes", "Priority by size", NOT_SURE), 0),
            CheckQuestion("os.sync", "Two processes each waiting for the other's resource is a…", listOf("Race condition", "Page fault", "Deadlock", NOT_SURE), 2),
            CheckQuestion("os.mem", "A page fault happens when…", listOf("The page is not in main memory", "The disk is full", "The CPU overheats", NOT_SURE), 0),
            CheckQuestion("os.fs", "An inode stores a file's…", listOf("Name only", "Metadata and block pointers", "Contents only", NOT_SURE), 1),
        ),
        mapOf(
            "os.proc" to listOf(q("A process in the 'ready' state is…", listOf("Waiting for the CPU", "Waiting for I/O", "Finished", "Being created"), 0, "Ready means it could run as soon as it gets the CPU.")),
            "os.sched" to listOf(q("Shortest Job First minimises…", listOf("Average waiting time", "Context switches", "Memory use", "Disk reads"), 0, "Running short jobs first lowers the average wait.")),
            "os.sync" to listOf(q("A semaphore is used to…", listOf("Control access to shared resources", "Speed up the CPU", "Store files", "Allocate pages"), 0, "wait() and signal() guard critical sections.")),
            "os.mem" to listOf(q("Virtual memory lets a program…", listOf("Use more memory than physically free", "Run without a CPU", "Skip the OS", "Avoid paging"), 0, "Pages not in use stay on disk until needed.")),
            "os.fs" to listOf(q("Which allocation suffers from external fragmentation?", listOf("Contiguous", "Linked", "Indexed", "None"), 0, "Contiguous files need one big free block.")),
        ),
    )

    val databases = Course(
        "db", "Database Systems",
        listOf(
            Topic("db.model", "Relational model and keys", "Pick primary and foreign keys for 2 tables"),
            Topic("db.sql", "SQL queries", "Write a JOIN with GROUP BY"),
            Topic("db.norm", "Normalisation", "Bring a table to 3NF"),
            Topic("db.tx", "Transactions and ACID", "Explain each ACID property with an example"),
            Topic("db.index", "Indexing", "Say when a B+ tree index helps"),
        ),
        listOf(
            CheckQuestion("db.model", "A foreign key…", listOf("Refers to a primary key in another table", "Must be unique in its table", "Is always a number", NOT_SURE), 0),
            CheckQuestion("db.sql", "Which SQL clause filters groups after GROUP BY?", listOf("WHERE", "HAVING", "ORDER BY", NOT_SURE), 1),
            CheckQuestion("db.norm", "Normalisation mainly reduces…", listOf("Query speed", "Data redundancy", "Number of users", NOT_SURE), 1),
            CheckQuestion("db.tx", "The 'A' in ACID stands for…", listOf("Availability", "Accuracy", "Atomicity", NOT_SURE), 2),
            CheckQuestion("db.index", "An index on a column mostly speeds up…", listOf("Searching by that column", "Inserting rows", "Deleting the table", NOT_SURE), 0),
        ),
        mapOf(
            "db.model" to listOf(q("A primary key must be…", listOf("Unique and not null", "A string", "Repeated", "Optional"), 0, "It identifies each row exactly once.")),
            "db.sql" to listOf(q("SELECT COUNT(*) FROM students; returns…", listOf("The number of rows", "All columns", "The first row", "Column names"), 0, "COUNT(*) counts rows.")),
            "db.norm" to listOf(q("2NF removes…", listOf("Partial dependencies on a composite key", "All keys", "Duplicate tables", "NULL values"), 0, "Every non-key column must depend on the whole key.")),
            "db.tx" to listOf(q("Durability means…", listOf("Committed changes survive a crash", "Queries run fast", "Data is encrypted", "Tables never change"), 0, "Once committed, it stays saved.")),
            "db.index" to listOf(q("Most databases implement indexes with…", listOf("B+ trees", "Linked lists", "Stacks", "Queues"), 0, "B+ trees keep keys sorted and shallow.")),
        ),
    )

    val networks = Course(
        "cn", "Computer Networks",
        listOf(
            Topic("cn.layers", "OSI and TCP/IP layers", "Place 6 protocols in the right layer"),
            Topic("cn.ip", "IP addressing and subnets", "Split a /24 network into 4 subnets"),
            Topic("cn.tcp", "TCP and UDP", "Explain the TCP three-way handshake"),
            Topic("cn.route", "Routing", "Run a distance-vector update by hand"),
            Topic("cn.app", "DNS and HTTP", "Trace what happens when you open a URL"),
        ),
        listOf(
            CheckQuestion("cn.layers", "Which layer is responsible for routing packets between networks?", listOf("Network layer", "Data link layer", "Application layer", NOT_SURE), 0),
            CheckQuestion("cn.ip", "How many bits are in an IPv4 address?", listOf("16", "32", "128", NOT_SURE), 1),
            CheckQuestion("cn.tcp", "Which protocol guarantees in-order delivery?", listOf("UDP", "IP", "TCP", NOT_SURE), 2),
            CheckQuestion("cn.route", "A router decides where to send a packet using its…", listOf("Routing table", "MAC address only", "DNS cache", NOT_SURE), 0),
            CheckQuestion("cn.app", "DNS turns…", listOf("IP addresses into MAC addresses", "Domain names into IP addresses", "HTTP into HTTPS", NOT_SURE), 1),
        ),
        mapOf(
            "cn.layers" to listOf(q("Ethernet works at which layer?", listOf("Data link", "Transport", "Session", "Application"), 0, "Ethernet frames move data on one link.")),
            "cn.ip" to listOf(q("A /24 subnet has how many addresses?", listOf("256", "24", "128", "1024"), 0, "8 host bits give 2⁸ = 256 addresses.")),
            "cn.tcp" to listOf(q("Video calls often use UDP because…", listOf("Low delay matters more than resending", "It is encrypted", "It is reliable", "It needs no IP"), 0, "Late packets are useless for live video.")),
            "cn.route" to listOf(q("OSPF is a… routing protocol", listOf("Link-state", "Distance-vector", "Application", "Physical"), 0, "Each router builds a full map of links.")),
            "cn.app" to listOf(q("HTTP status 404 means…", listOf("Not found", "OK", "Server error", "Redirect"), 0, "The server has no resource at that address.")),
        ),
    )

    val machineLearning = Course(
        "ml", "Machine Learning Basics",
        listOf(
            Topic("ml.types", "Supervised and unsupervised learning", "Sort 6 tasks into the right type"),
            Topic("ml.reg", "Linear regression", "Fit a line by hand to 4 points"),
            Topic("ml.cls", "Classification", "Explain logistic regression in one paragraph"),
            Topic("ml.eval", "Evaluation and overfitting", "Read a confusion matrix"),
            Topic("ml.nn", "Neural networks", "Describe a forward pass for one layer"),
        ),
        listOf(
            CheckQuestion("ml.types", "Grouping customers without labels is…", listOf("Supervised learning", "Unsupervised learning", "Reinforcement learning", NOT_SURE), 1),
            CheckQuestion("ml.reg", "Linear regression predicts a…", listOf("Number", "Class label", "Cluster", NOT_SURE), 0),
            CheckQuestion("ml.cls", "Spam or not spam is a… problem", listOf("Regression", "Clustering", "Classification", NOT_SURE), 2),
            CheckQuestion("ml.eval", "High training accuracy but low test accuracy usually means…", listOf("Overfitting", "Underfitting", "Perfect model", NOT_SURE), 0),
            CheckQuestion("ml.nn", "An activation function adds…", listOf("More data", "Non-linearity", "Labels", NOT_SURE), 1),
        ),
        mapOf(
            "ml.types" to listOf(q("Training data with correct answers is used in…", listOf("Supervised learning", "Unsupervised learning", "Clustering only", "No learning"), 0, "Labels guide the model.")),
            "ml.reg" to listOf(q("Mean squared error measures…", listOf("Average squared prediction error", "Accuracy", "Number of features", "Training time"), 0, "Smaller MSE means predictions are closer.")),
            "ml.cls" to listOf(q("Logistic regression outputs a…", listOf("Probability between 0 and 1", "Any real number", "Cluster ID", "Image"), 0, "The sigmoid squashes scores into 0–1.")),
            "ml.eval" to listOf(q("Precision is…", listOf("True positives / predicted positives", "True positives / all data", "Errors / total", "Recall squared"), 0, "Of what you flagged, how much was right.")),
            "ml.nn" to listOf(q("Backpropagation computes…", listOf("Gradients of the loss", "The dataset", "Labels", "Test accuracy"), 0, "Gradients tell how to change each weight.")),
        ),
    )

    val calculus = Course(
        "calc", "Calculus",
        listOf(
            Topic("calc.lim", "Limits", "Evaluate 3 limits, including one 0/0 form"),
            Topic("calc.der", "Derivatives", "Differentiate using product and chain rules"),
            Topic("calc.app", "Applications of derivatives", "Find the maximum of a function"),
            Topic("calc.int", "Integration", "Integrate by substitution"),
            Topic("calc.area", "Definite integrals and area", "Find the area between two curves"),
        ),
        listOf(
            CheckQuestion("calc.lim", "What is the limit of (x² − 1)/(x − 1) as x → 1?", listOf("0", "2", "It does not exist", NOT_SURE), 1),
            CheckQuestion("calc.der", "The derivative of x³ is…", listOf("3x²", "x²", "3x", NOT_SURE), 0),
            CheckQuestion("calc.app", "At a local maximum, the derivative is…", listOf("Positive", "Negative", "Zero", NOT_SURE), 2),
            CheckQuestion("calc.int", "∫ 2x dx equals…", listOf("x² + C", "2 + C", "2x² + C", NOT_SURE), 0),
            CheckQuestion("calc.area", "∫ from 0 to 1 of x dx equals…", listOf("1", "1/2", "2", NOT_SURE), 1),
        ),
        mapOf(
            "calc.lim" to listOf(q("As x → ∞, 1/x approaches…", listOf("0", "1", "∞", "−1"), 0, "The fraction keeps shrinking.")),
            "calc.der" to listOf(q("d/dx of sin x is…", listOf("cos x", "−cos x", "sin x", "tan x"), 0, "A standard derivative to remember.")),
            "calc.app" to listOf(q("If f′′(x) > 0 at a critical point, it is a…", listOf("Minimum", "Maximum", "Inflection only", "Discontinuity"), 0, "The curve bends upwards there.")),
            "calc.int" to listOf(q("∫ eˣ dx equals…", listOf("eˣ + C", "x eˣ + C", "ln x + C", "1/eˣ + C"), 0, "eˣ is its own antiderivative.")),
            "calc.area" to listOf(q("A definite integral of a positive function gives…", listOf("The area under the curve", "The slope", "The maximum", "The limit"), 0, "It sums thin strips under the curve.")),
        ),
    )

    val builtIn = listOf(dataStructures, discreteMaths, python, operatingSystems, databases, networks, machineLearning, calculus)
    private val custom = mutableMapOf<String, Course>()

    fun register(course: Course) { custom[course.id] = course }
    fun all(): List<Course> = builtIn + custom.values
    fun get(id: String): Course = builtIn.firstOrNull { it.id == id } ?: custom[id] ?: dataStructures
    fun topic(id: String): Topic = all().firstNotNullOfOrNull { c -> c.topics.firstOrNull { it.id == id } } ?: Topic(id, id, "")
    fun courseOf(topicId: String): Course = all().firstOrNull { c -> c.topics.any { it.id == topicId } } ?: dataStructures
}

object Content {
    /** Shortcut used across the UI. */
    fun topic(id: String) = Courses.topic(id)

    val goalTypes = listOf(
        "Exam prep" to "A test or end-sem",
        "Career" to "Get job-ready",
        "New skill" to "Learn something new",
        "Certification" to "Pass a certificate",
        "Tech roadmap" to "Step-by-step path",
        "Lifelong learning" to "Learn for fun",
    )
    val studyLevels = listOf("School", "Undergraduate", "Postgraduate", "Working")
    val deadlines = listOf("In 2 weeks" to 14, "In a month" to 30, "In 3 months" to 90, "In 6 months" to 182, "In 12 months" to 365, "No date" to 0)
    val minuteOptions = listOf(15, 30, 45, 60, 120, 300, 360)
    fun minutesLabel(m: Int) = if (m >= 60 && m % 60 == 0) "${m / 60} h" else "$m min"
    val interests = listOf(
        "Coding", "Maths", "Physics", "Chemistry", "Biology", "Electronics", "Data science", "AI", "Design", "Robotics",
        "Business", "Finance", "Writing", "Music", "Art", "Gaming", "Sports", "Space", "Medicine", "History",
    )

    /** UI languages. Llama 3.2 officially supports English and Hindi from this list, so AI text is good only there. */
    data class Language(val code: String, val native: String, val english: String, val aiGood: Boolean)
    val languages = listOf(
        Language("en", "English", "English", true),
        Language("hi", "हिन्दी", "Hindi", true),
        Language("bn", "বাংলা", "Bengali", false),
        Language("te", "తెలుగు", "Telugu", false),
        Language("mr", "मराठी", "Marathi", false),
        Language("ta", "தமிழ்", "Tamil", false),
        Language("gu", "ગુજરાતી", "Gujarati", false),
        Language("kn", "ಕನ್ನಡ", "Kannada", false),
        Language("ml", "മലയാളം", "Malayalam", false),
        Language("or", "ଓଡ଼ିଆ", "Odia", false),
        Language("pa", "ਪੰਜਾਬੀ", "Punjabi", false),
        Language("as", "অসমীয়া", "Assamese", false),
        Language("ur", "اردو", "Urdu", false),
    )
}
