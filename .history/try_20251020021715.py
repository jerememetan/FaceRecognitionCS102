def group_numbers(list, threshold):
    list_num = []
    final_list = []
    total = 0
    for i in range(len(list)):    
        while total < threshold:
            total += list[i]
            list_num.append(list[i])
            break
        final_list.append(list_num)
        # list = list[i:]   
        total = 0
        # list_num = []
    
    
    print(final_list)
        

group_numbers([1, 3, 2, 4, 3, 2, 3, 6], 6)