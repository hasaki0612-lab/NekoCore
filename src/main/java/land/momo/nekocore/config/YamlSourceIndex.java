package land.momo.nekocore.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.*;
import java.util.*;

/** 复用 Paper 已有的 YAML 解析器定位原文；别名、合并、重复键不猜行号。 */
public final class YamlSourceIndex {
    private final Map<String, Integer> lines = new HashMap<>();
    private final Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean reliable = true;

    public static YamlSourceIndex of(String source) {
        var index = new YamlSourceIndex();
        try {
            var options = new LoaderOptions();
            Node root = new Yaml(new SafeConstructor(options)).compose(new java.io.StringReader(source));
            index.walk(root, "");
        } catch (RuntimeException error) { index.reliable = false; }
        if (!index.reliable) index.lines.clear();
        return index;
    }
    public OptionalInt line(String path) {
        Integer line = lines.get(path);
        return line == null ? OptionalInt.empty() : OptionalInt.of(line);
    }
    private void walk(Node node, String path) {
        if (node == null) return;
        if (!seen.add(node)) { reliable = false; return; }
        if (node instanceof MappingNode mapping) {
            Set<String> keys = new HashSet<>();
            for (NodeTuple pair : mapping.getValue()) {
                if (!(pair.getKeyNode() instanceof ScalarNode key) || Tag.MERGE.equals(key.getTag())) {
                    reliable = false; continue;
                }
                String name = key.getValue();
                if (!keys.add(name) || name.contains(".")) reliable = false;
                String child = path.isEmpty() ? name : path + "." + name;
                if (key.getStartMark() != null) lines.put(child, key.getStartMark().getLine() + 1);
                walk(pair.getValueNode(), child);
            }
        } else if (node instanceof SequenceNode sequence) {
            for (Node value : sequence.getValue()) walk(value, path);
        }
    }
}
