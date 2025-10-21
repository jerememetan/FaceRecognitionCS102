def group_numbers(list, threshold):
    copy = list.copy()
    copy.sort()
    list_num = []
    final_list = []
    total = 0
    i = 0
    j = len(list) - 1
    while i < j:
        total = 0
        list_num = []
        isI = False
        while total < threshold:
            if isI and total + copy[i] <= threshold:
                
            
            if i < j:
                break
            
    print(final_list)
        

group_numbers([1, 3, 4, 3, 5, 2, 5, 6], 6)