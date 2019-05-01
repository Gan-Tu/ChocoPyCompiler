def range(start:int, end:int) -> [int]:
    result:[int] = None
    i:int = 0
    result = []
    i = start
    while i < end:
        result = result + [i]
        i = i + 1
    return result

def quicksort(arr:[int]) -> [int]:
    def partition(low:int, high:int) -> int:
        nonlocal arr
        i:int = 0
        j:int = 0
        pivot:int = 0
        tmp:int = 0

        i = low - 1
        pivot = arr[high]

        for j in range(low, high):
            if arr[j] <= pivot:
                i = i+1
                tmp = arr[i]
                arr[i] = arr[j]
                arr[j] = tmp

        tmp = arr[i+1]
        arr[i+1] = arr[high]
        arr[high] = tmp
        return i + 1
    def _quicksort(low:int, high:int):
        pi:int = 0
        if low < high:
            pi = partition(low,high)
            _quicksort(low, pi-1)
            _quicksort(pi+1, high)
    _quicksort(0, len(arr) - 1)
    return arr

def is_sorted(arr:[int]) -> bool:
    i:int = 0
    for i in range(1, len(arr)-1):
        if arr[i] < arr[i-1]:
            return False
    return True

random_list:[int] = None
sorted_list:[int] = None
i:int = 0

random_list = [413, 1, 322, 175, 12, 81, 433, 365, 116, 342, 324, 435, 204, 4, 8, 199, 256, 266, 316, 57, 454, 191, 10, 97, 80, 28, 339, 382, 444, 236, 450, 110, 105, 442, 489, 492, 167, 280, 295, 78, 355, 436, 269, 156, 458, 278, 331, 79, 301, 132, 389, 353, 260, 240, 69, 315, 246, 314, 337, 329, 194, 114, 292, 290, 72, 33, 177, 434, 244, 369, 328, 405, 268, 117, 410, 397, 367, 363, 246, 424, 8, 137, 263, 447, 321, 159, 488, 257, 193, 303, 143, 202, 322, 282, 250, 55, 80, 240, 290, 34]

sorted_list = quicksort(random_list)

print(is_sorted(random_list))
print(is_sorted(sorted_list))

while i < len(sorted_list):
    print(sorted_list[i])
    i = i +1
