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
            if isI and total + copy[i] <= threshold and i < j:
                total += copy[i]
                list_num.append(copy[i])
                i += 1
            elif total + copy[j] <= threshold and i < j:
                total += copy[j]
                list_num.append(copy[j])
                j -= 1
        
            if i < j:
                break
            
    print(final_list)
        

group_numbers([1, 3, 4, 3, 5, 2, 5, 6], 6)