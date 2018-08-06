/*
 * Portions Copyright 2013-2015 Technology Concepts & Design, Inc
 * Portions Copyright 2015-2018 ZomboDB, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package llc.zombodb.query_parser;

import llc.zombodb.query_parser.metadata.IndexMetadata;
import llc.zombodb.query_parser.metadata.IndexMetadataManager;
import llc.zombodb.query_parser.utils.Utils;

import java.util.*;

public class QueryParserNode extends SimpleNode implements Iterable<QueryParserNode>, Cloneable {
    public enum Operator {
        EQ,
        NE,
        LT,
        GT,
        LTE,
        GTE,
        CONTAINS,
        REGEX,
        CONCEPT,
        FUZZY_CONCEPT
    }

    protected String fieldname;
    private String typename;
    protected float boost = 0.0f;
    protected int fuzzyness = 0;
    protected int distance = 0;
    protected boolean ordered = true;
    protected Operator operator = Operator.CONTAINS;

    private ASTIndexLink indexLink;

    public QueryParserNode copy() {
        try {
            QueryParserNode copy = (QueryParserNode) super.clone();

            if (children != null) {
                copy.children = new TreeMap<>();
                for (Map.Entry<Number, Node> entry : children.entrySet()) {
                    Node childCopy = ((QueryParserNode) entry.getValue()).copy();
                    copy.jjtAddChild(childCopy, entry.getKey().intValue());
                    childCopy.jjtSetParent(copy);
                }
            }

            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public QueryParserNode(int i) {
        super(i);
    }

    public QueryParserNode(QueryParser p, int i) {
        super(p, i);
    }

    public String getFieldname() {
        return fieldname;
    }

    public String getTypename() {
        return typename;
    }

    public Operator getOperator() {
        return operator;
    }

    public float getBoost() {
        return boost;
    }

    public void setBoost(float boost) {
        this.boost = boost;
    }

    public int getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    public boolean isOrdered() {
        return ordered;
    }

    public void setOrdered(boolean ordered) {
        this.ordered = ordered;
    }

    public Object getValue() {
        Object value = jjtGetValue();
        return value instanceof String ? Utils.unescape((String) value) : value;
    }

    public String getEscapedValue() {
        Object value = jjtGetValue();
        if (value instanceof String)
            return (String) value;
        throw new RuntimeException("Value is not a String: " + value);
    }

    public boolean isStringValue() {
        return jjtGetValue() instanceof String;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public void setFieldname(String fieldname) {
        this.fieldname = fieldname;
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }

    public int getFuzzyness() {
        return fuzzyness;
    }

    public void setFuzzyness(int fuzzyness) {
        this.fuzzyness = fuzzyness;
    }

    public boolean isNested(IndexMetadataManager metadataManager) {
        if (fieldname != null && fieldname.contains(".")) {
            IndexMetadata md = metadataManager.getMetadataForField(fieldname);
            if (md != null && md.isMultiField(fieldname))
                return false;
        }
        return fieldname != null && fieldname.contains(".");
    }

    public String getNestedPath() {
        if (fieldname == null)
            return null;
        int idx = fieldname.indexOf('.');
        if (idx == -1)
            return null;
        return fieldname.substring(0, idx);
    }

    public List<String> getNestedPaths() {
        if (fieldname == null || !fieldname.contains("."))
            return Collections.emptyList();

        List<String> rc = new ArrayList<>();
        String[] parts = fieldname.split("[.]");
        StringBuilder tmp = new StringBuilder();

        for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) tmp.append('.');
            tmp.append(parts[i]);
            rc.add(tmp.toString());
        }

        return rc;
    }

    public ASTIndexLink getIndexLink() {
        return indexLink;
    }

    public void setIndexLink(ASTIndexLink indexLink) {
        this.indexLink = indexLink;
        if (children != null)
            for (Node child : children.values())
                ((QueryParserNode) child).setIndexLink(indexLink);
    }

    public QueryParserNode getChild(int idx) {
        return (QueryParserNode) jjtGetChild(idx);
    }

    public boolean hasChildren() {
        return jjtGetNumChildren() > 0;
    }

    QueryParserNode getChild(Class t) {
        if (this.getClass().isAssignableFrom(t))
            return this;
        for (QueryParserNode child : this) {
            if (child.getClass().isAssignableFrom(t))
                return child;
            else {
                QueryParserNode node = child.getChild(t);
                if (node != null)
                    return node;
            }
        }
        return null;
    }

    public <T> Collection<T> getChildrenOfType(Class<T> t) {
        return getChildrenOfType(this, t, new ArrayList<T>(), true);
    }

    public <T> Collection<T> getChildrenOfType(Class<T> t, boolean recurse) {
        return getChildrenOfType(this, t, new ArrayList<T>(), recurse);
    }

    private <T> List<T> getChildrenOfType(QueryParserNode node, Class t, List<T> list, boolean recurse) {
        for (QueryParserNode child : node) {
            boolean found = t.isAssignableFrom(child.getClass());

            if (found)
                list.add((T) child);

            if (!found || recurse)
                getChildrenOfType(child, t, list, recurse);
        }
        return list;
    }

    public int countNodes() {
        int sum = jjtGetNumChildren();

        for (QueryParserNode node : this)
            sum += node.countNodes();

        return sum;
    }

    public void forceFieldname(String fieldname) {
        this.fieldname = fieldname;
        for (QueryParserNode child : this)
            child.forceFieldname(fieldname);
    }

    public void adoptChildren(QueryParserNode node) {
        for (QueryParserNode child : node) {
            jjtAddChild(child, jjtGetNumChildren());
            child.jjtSetParent(this);
        }
    }

    public void removeNode(int idx) {
        if (children == null)
            return;
        children.remove(idx);
    }

    public void removeNode(QueryParserNode node) {
        if (children == null)
            return;

        for (Iterator<Node> itr = children.values().iterator(); itr.hasNext(); )
            if (itr.next() == node)
                itr.remove();
    }

    public Collection<Object> getChildValues() {
        return new AbstractCollection<Object>() {
            final int many = jjtGetNumChildren();

            @Override
            public Iterator<Object> iterator() {
                return new Iterator<Object>() {
                    int cur = 0;

                    @Override
                    public boolean hasNext() {
                        return cur < many;
                    }

                    @Override
                    public Object next() {
                        return ((QueryParserNode) jjtGetChild(cur++)).getValue();
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            @Override
            public int size() {
                return many;
            }
        };
    }

    public Map<Number, Node> getChildren() {
        return children;
    }

    @Override
    public Iterator<QueryParserNode> iterator() {
        return new Iterator<QueryParserNode>() {
            final int many = jjtGetNumChildren();
            int cur = 0;

            @Override
            public boolean hasNext() {
                return cur < many;
            }

            @Override
            public QueryParserNode next() {
                return (QueryParserNode) jjtGetChild(cur++);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public String getDescription() {
        return fieldname + " " + operator + " " + "\"" + value + "\"";
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (typename != null) {
            sb.append("type=").append(typename);
        }
        if (fieldname != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("fieldname=").append(fieldname);
        }
        if (operator != null && fieldname != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("operator=").append(operator);
        }
        if (value != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("value=").append(value);
        }
        if (fuzzyness != 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("fuzz=").append(fuzzyness);
        }
        if (distance > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("distance=").append(distance);
        }
        if (!ordered) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("ordered=").append(ordered);
        }
        if (boost > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("boost=").append(boost);
        }
        if (indexLink != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("index=").append(indexLink.getIndexName());
        }

        if (sb.length() > 0) {
            sb.insert(0, " (");
            sb.append(")");
            sb.insert(0, super.toString());
            return sb.toString();
        } else {
            return super.toString();
        }
    }
}
