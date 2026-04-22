public class HashMapImpl<K, V> {

    private static final int DEFAULT_CAPACITY = 4;
    private Node<K, V>[] arr = new Node[DEFAULT_CAPACITY];

    
    // TODO: Define a Node class (key, value, next)
    class Node<K, V> {
        private K key;

        public K getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }
        private V value;
        private Node next;

        public Node(K key, Node next, V value) {
            this.key = key;
            this.next = next;
            this.value = value;
        }
    }

    // TODO: Create the bucket array
    public HashMapImpl() {
    }

    // TODO: Implement put(key, value)
    public void put(String key, String val) {
        int hash = key.hashCode();
        int index = hash & (DEFAULT_CAPACITY - 1);
        Node node = new Node(key, null,val);
        if (arr[index] == null)
            arr[index] = node;
        else {
            Node temp = arr[index];
            while (temp.next != null) {
                if (key.equals(temp.key)){
                    temp.value = val;
                    return;
                }
                temp = temp.next;
            }
            if (key.equals(temp.key))
                temp.value = val;
            else
                temp.next = node;
        }
    }

    // TODO: Implement get(key)
    public Object get(String key) {
        int hash = key.hashCode();
        int index = hash & (DEFAULT_CAPACITY - 1);

        Node node = arr[index];
        if (node == null)
            return null;
        else if (node.next == null)
            return node.getValue();
        else {
            Node temp = node;
            while(temp != null) {
                if (key.equals(temp.key)){
                    return temp.getValue();
                }
                temp = temp.next;
            }
        }
        return null;
    }

    public static void main(String[] args) {
        HashMapImpl<String, String> map = new HashMapImpl<>();
        map.put("name", "Murali");
        map.put("city", "Hyderabad");
        map.put("city","tokyo");

        System.out.println(map.get("name"));   // should print: Murali
        System.out.println(map.get("city"));    // should print: tokyo
        System.out.println(map.get("age"));     // should print: null
    }
}